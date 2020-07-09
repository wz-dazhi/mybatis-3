package org.apache.ibatis.test;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;

/**
 * @projectName: mybatis-3
 * @package: org.apache.ibatis.test
 * @className: XPathTest
 * @description:
 * @author: Zhi
 * @date: 2020/7/7 11:54
 * @version: 1.0
 */
public class XPathTest {

  public static void main(String[] args) throws Exception {
    //创建DocumentBuilder
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

    //从文件或数据流创建一个文档
    byte[] buf = ("<?xml version=\"1.0\"?>" +
      "<class>" +
      " <student>张三</student>" +
      "</class>").getBytes("UTF-8");
    ByteArrayInputStream input = new ByteArrayInputStream(buf);
    Document doc = documentBuilder.parse(input);
    //构建XPath
    XPath xPath = XPathFactory.newInstance().newXPath();

    //准备路径表达式，并计算它
    String expression = "/class/student";
    NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);

    //遍历节点列表
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node nNode = nodeList.item(i);
      System.out.println("Node class: " + nNode.getClass() + ", name: " + nNode.getNodeName() + ", value: " + nNode.getNodeValue() + ", content: " + nNode.getTextContent());
    }

  }
}
