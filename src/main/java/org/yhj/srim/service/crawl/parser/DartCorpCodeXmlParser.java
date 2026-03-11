package org.yhj.srim.service.crawl.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class DartCorpCodeXmlParser {

    public List<Object[]> parse(InputStream is) throws Exception {
        List<Object[]> batch = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(is);
        doc.getDocumentElement().normalize();

        NodeList list = doc.getElementsByTagName("list");
        log.info("CORPCODE.xml list 노드 개수 = {}", list.getLength());

        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);

            String corpCode = getTagText(e, "corp_code");
            String corpName = getTagText(e, "corp_name");
            String stockCode = getTagText(e, "stock_code");

            if (stockCode == null || stockCode.isBlank()) {
                continue;
            }

            corpCode = corpCode != null ? corpCode.trim() : null;
            corpName = corpName != null ? corpName.trim() : null;
            stockCode = stockCode.trim();

            batch.add(new Object[]{corpCode, corpName, stockCode});
        }

        return batch;
    }

    private String getTagText(Element e, String tagName) {
        NodeList nl = e.getElementsByTagName(tagName);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent();
    }
}
