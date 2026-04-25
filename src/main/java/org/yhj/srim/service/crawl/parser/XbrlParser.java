package org.yhj.srim.service.crawl.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.yhj.srim.service.crawl.dto.XbrlDimension;
import org.yhj.srim.service.crawl.dto.XbrlParseResult;
import org.yhj.srim.service.crawl.dto.XbrlParsedContext;
import org.yhj.srim.service.crawl.dto.XbrlParsedFact;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@Slf4j
public class XbrlParser {

    public XbrlParseResult parse(byte[] archiveBytes) {
        Map<String, XbrlParsedContext> contextsByRef = new LinkedHashMap<>();
        List<XbrlParsedFact> facts = new ArrayList<>();
        List<String> zipEntries = new ArrayList<>();
        List<String> parsedEntries = new ArrayList<>();
        List<String> skippedXmlEntries = new ArrayList<>();
        String taxonomyVersion = null;
        int orderHint = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                zipEntries.add(entry.getName());

                if (entry.isDirectory() || !isXmlEntry(entry.getName())) {
                    continue;
                }

                byte[] entryBytes = zipInputStream.readAllBytes();
                Document document = parseXml(entryBytes);
                if (document == null) {
                    skippedXmlEntries.add(entry.getName());
                    continue;
                }

                parsedEntries.add(entry.getName());

                if (taxonomyVersion == null) {
                    taxonomyVersion = extractTaxonomyVersion(document);
                }

                collectContexts(document, contextsByRef);
                orderHint = collectFacts(document, entry.getName(), contextsByRef, facts, orderHint);
            }
        } catch (Exception e) {
            throw new IllegalStateException("XBRL zip 파싱에 실패했습니다.", e);
        }

        if (facts.isEmpty()) {
            log.warn("XBRL zip 파싱 결과가 비어 있습니다. zipEntries={}, parsedEntries={}, skippedXmlEntries={}, taxonomyVersion={}",
                    zipEntries, parsedEntries, skippedXmlEntries, taxonomyVersion);
        } else if (log.isDebugEnabled()) {
            log.debug("XBRL zip 파싱 엔트리 요약 zipEntries={}, parsedEntries={}, skippedXmlEntries={}",
                    zipEntries, parsedEntries, skippedXmlEntries);
        }

        return new XbrlParseResult(List.copyOf(contextsByRef.values()), List.copyOf(facts), taxonomyVersion);
    }

    private boolean isXmlEntry(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xml") || lower.endsWith(".xbrl");
    }

    private Document parseXml(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new InputStreamReader(
                    new ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8
            )));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractTaxonomyVersion(Document document) {
        NodeList schemaRefs = document.getElementsByTagNameNS("*", "schemaRef");
        if (schemaRefs.getLength() == 0) {
            return null;
        }

        Element schemaRef = (Element) schemaRefs.item(0);
        String href = schemaRef.getAttributeNS("http://www.w3.org/1999/xlink", "href");
        if (href == null || href.isBlank()) {
            href = schemaRef.getAttribute("xlink:href");
        }
        return href == null || href.isBlank() ? null : href;
    }

    private void collectContexts(Document document, Map<String, XbrlParsedContext> contextsByRef) {
        NodeList contextNodes = document.getElementsByTagNameNS("*", "context");
        for (int i = 0; i < contextNodes.getLength(); i++) {
            Element context = (Element) contextNodes.item(i);
            String contextRef = context.getAttribute("id");
            if (contextRef == null || contextRef.isBlank() || contextsByRef.containsKey(contextRef)) {
                continue;
            }

            LocalDate periodStart = parseLocalDate(findTextByLocalName(context, "startDate"));
            LocalDate periodEnd = parseLocalDate(findTextByLocalName(context, "endDate"));
            LocalDate instantDate = parseLocalDate(findTextByLocalName(context, "instant"));
            String periodType = instantDate != null ? "instant" : "duration";
            String entityIdentifier = findTextByLocalName(context, "identifier");
            List<XbrlDimension> dimensions = extractDimensions(context);

            contextsByRef.put(contextRef, new XbrlParsedContext(
                    contextRef,
                    entityIdentifier,
                    periodType,
                    periodStart,
                    periodEnd,
                    instantDate,
                    dimensions,
                    buildMemberSignature(dimensions)
            ));
        }
    }

    private int collectFacts(Document document,
                             String statementRole,
                             Map<String, XbrlParsedContext> contextsByRef,
                             List<XbrlParsedFact> facts,
                             int startOrderHint) {
        int orderHint = startOrderHint;
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(document.getDocumentElement());

        while (!stack.isEmpty()) {
            Element element = stack.pop();

            if (hasContextRef(element) && !isInfrastructureElement(element)) {
                String valueRaw = normalizeValue(element.getTextContent());
                String contextRef = element.getAttribute("contextRef");
                XbrlParsedContext context = contextsByRef.get(contextRef);

                facts.add(new XbrlParsedFact(
                        contextRef,
                        element.getTagName(),
                        defaultString(element.getLocalName(), element.getTagName()),
                        null,
                        statementRole,
                        blankToNull(element.getAttribute("unitRef")),
                        blankToNull(element.getAttribute("decimals")),
                        valueRaw,
                        parseNumeric(valueRaw),
                        isNil(element),
                        context == null ? null : context.memberSignature(),
                        ++orderHint
                ));
            }

            NodeList childNodes = element.getChildNodes();
            for (int i = childNodes.getLength() - 1; i >= 0; i--) {
                Node child = childNodes.item(i);
                if (child instanceof Element childElement) {
                    stack.push(childElement);
                }
            }
        }

        return orderHint;
    }

    private boolean hasContextRef(Element element) {
        return element.hasAttribute("contextRef");
    }

    private boolean isInfrastructureElement(Element element) {
        String localName = defaultString(element.getLocalName(), element.getTagName());
        return Set.of("context", "unit", "schemaRef", "roleRef", "arcroleRef").contains(localName);
    }

    private List<XbrlDimension> extractDimensions(Element context) {
        List<XbrlDimension> dimensions = new ArrayList<>();
        NodeList explicitMembers = context.getElementsByTagNameNS("*", "explicitMember");
        for (int i = 0; i < explicitMembers.getLength(); i++) {
            Element member = (Element) explicitMembers.item(i);
            dimensions.add(new XbrlDimension(
                    blankToNull(member.getAttribute("dimension")),
                    normalizeValue(member.getTextContent()),
                    false
            ));
        }

        NodeList typedMembers = context.getElementsByTagNameNS("*", "typedMember");
        for (int i = 0; i < typedMembers.getLength(); i++) {
            Element member = (Element) typedMembers.item(i);
            dimensions.add(new XbrlDimension(
                    blankToNull(member.getAttribute("dimension")),
                    normalizeValue(member.getTextContent()),
                    true
            ));
        }

        return List.copyOf(dimensions);
    }

    private String buildMemberSignature(List<XbrlDimension> dimensions) {
        if (dimensions.isEmpty()) {
            return null;
        }

        return dimensions.stream()
                .map(dimension -> defaultString(dimension.axis(), "-") + "=" + defaultString(dimension.member(), "-"))
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse(null);
    }

    private String findTextByLocalName(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return normalizeValue(nodes.item(0).getTextContent());
    }

    private LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseNumeric(String valueRaw) {
        if (valueRaw == null || valueRaw.isBlank()) {
            return null;
        }

        String normalized = valueRaw.replace(",", "").trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isNil(Element element) {
        if ("true".equalsIgnoreCase(element.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil"))) {
            return true;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if ("nil".equalsIgnoreCase(defaultString(attribute.getLocalName(), attribute.getNodeName()))
                    && "true".equalsIgnoreCase(attribute.getNodeValue())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
