/*
 * Tanaguru - Automated webpage assessment
 * Copyright (C) 2008-2011  Open-S Company
 *
 * This file is part of Tanaguru.
 *
 * Tanaguru is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact us by mail: open-s AT open-s DOT com
 */
package org.opens.tanaguru.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import org.opens.tanaguru.entity.audit.ProcessRemark;
import org.opens.tanaguru.entity.audit.SSP;
import org.opens.tanaguru.entity.audit.TestSolution;
import org.opens.tanaguru.entity.subject.WebResource;
import org.opens.tanaguru.exception.IncoherentValueDomainsException;
import org.opens.tanaguru.ruleimplementation.RuleHelper;
import org.opens.tanaguru.service.ProcessRemarkService;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * @author jkowalczyk
 */
public class DOMHandlerImpl implements DOMHandler {

    private static final String ATTRIBUTE_MISSING_MSG_CODE = "AttributeMissing";
    private static final String CHILD_NODE_MISSING_MSG_CODE ="ChildNodeMissing";
    private Document document;
    private boolean initialized = false;
    private List<Node> selectedElementList;
    private SSP ssp;
    private XPath xpath;
    private Map<Integer, String> sourceCodeWithLine;
    private static final Pattern NON_ALPHANUMERIC_PATTERN =
              Pattern.compile("[^\\p{L}]+");
//            Pattern.compile("[\\W_]+");

    private ProcessRemarkService processRemarkService;

    /**
     * The message code defined by the user
     */
    private String messageCode;
    @Override
    public String getMessageCode() {
        return messageCode;
    }

    @Override
    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }

    public DOMHandlerImpl() {
        super();
    }

    public DOMHandlerImpl(SSP ssp) {
        this.ssp = ssp;
    }

    @Override
    public void addSourceCodeRemark(TestSolution processResult, Node node,
            String messageCode, String attributeName) {
        processRemarkService.addSourceCodeRemark(
                processResult,
                node,
                messageCode,
                attributeName);
    }

    @Override
    public DOMHandler beginSelection() {
        initialize();
        messageCode = null;
        selectedElementList = new ArrayList<Node>();
        processRemarkService.initializeService(document, ssp.getDOM());
        return this;
    }

    @Override
    public TestSolution checkAttributeExists(String attributeName) {
        if (messageCode == null) {
            messageCode = ATTRIBUTE_MISSING_MSG_CODE;
        }
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution processResult = TestSolution.PASSED;
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute == null) {
                processResult = TestSolution.FAILED;
                addSourceCodeRemark(processResult, workingElement,
                        messageCode, attributeName);
            }
            resultSet.add(processResult);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkAttributeValueExpression(String attributeName,
            String regexp) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute != null) {
                if (attribute.getNodeValue().matches(regexp)) {
                    result = TestSolution.FAILED;
                    addSourceCodeRemark(result, workingElement,
                            "NotMatchExpression", attribute.getNodeValue());
                }
            } else {
                result = TestSolution.NOT_APPLICABLE;
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkAttributeValueLengthLower(String attributeName,
            int length, TestSolution defaultFailResult) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            String textContent = workingElement.getTextContent();
            if (textContent.length() > length) {
                result = defaultFailResult;
                addSourceCodeRemark(result, workingElement, "LengthTooLong",
                        textContent);
            }
            resultSet.add(result);
        }

        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkAttributeValueNotEmpty(String attributeName) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute != null) {
                if (attribute.getNodeValue().length() == 0) {
                    result = TestSolution.FAILED;
                    addSourceCodeRemark(result, workingElement, "ValueEmpty",
                            attributeName);
                }
            } else {
                result = TestSolution.NOT_APPLICABLE;
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkAttributeValueIsEmpty(String attributeName) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute != null) {
                if (attribute.getNodeValue().length() != 0) {
                    result = TestSolution.FAILED;
                    addSourceCodeRemark(result, workingElement, "ValueNotEmpty",
                            attributeName);
                }
            } else {
                result = TestSolution.NOT_APPLICABLE;
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkChildNodeExists(String childNodeName) {
        if (messageCode == null) {
            messageCode = CHILD_NODE_MISSING_MSG_CODE;
        }
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            NodeList childNodes = workingElement.getChildNodes();
            boolean found = false;
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                if (childNode.getNodeName().equalsIgnoreCase(childNodeName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result = TestSolution.FAILED;
                addSourceCodeRemark(result, workingElement, messageCode,
                        childNodeName);
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkChildNodeExistsRecursively(String childNodeName) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            boolean found = false;
            NodeList childNodes = workingElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                if (checkChildNodeExistsRecursively(childNodeName, childNodes.item(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result = TestSolution.FAILED;
                addSourceCodeRemark(result, workingElement, "ChildNodeMissing",
                        childNodeName);
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    protected boolean checkChildNodeExistsRecursively(String childNodeName,
            Node node) {// XXX
        if (node.getNodeName().equalsIgnoreCase(childNodeName)) {
            return true;
        }
        NodeList nodes = node.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (checkChildNodeExistsRecursively(childNodeName, nodes.item(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TestSolution checkContentNotEmpty() {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            if (workingElement.getTextContent().trim().isEmpty()
                    && (workingElement.getChildNodes().getLength() == 0
                    || (workingElement.getChildNodes().getLength() == 1
                    && workingElement.getChildNodes().item(0).getNodeName().equalsIgnoreCase("#text")))) {
                result = TestSolution.FAILED;
                addSourceCodeRemark(result, workingElement, "ValueEmpty",
                        workingElement.getNodeName());
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkEachWithXpath(String expr) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node node : selectedElementList) {
            TestSolution tempResult = TestSolution.PASSED;
            try {
                XPathExpression xPathExpression = xpath.compile(expr);
                Boolean check = (Boolean) xPathExpression.evaluate(node,
                        XPathConstants.BOOLEAN);
                if (!check.booleanValue()) {
                    tempResult = TestSolution.FAILED;
                    // addSourceCodeRemark(result, node,
                    // "wrong value, does not respect xpath expression : " +
                    // expr, node.getNodeValue());
                }
            } catch (XPathExpressionException ex) {
                Logger.getLogger(DOMHandlerImpl.class.getName()).log(
                        Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
            resultSet.add(tempResult);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkNodeValue(Collection<String> blacklist,
            Collection<String> whitelist) {
        return  checkNodeValue(blacklist, whitelist, TestSolution.FAILED,
                "BlackListedValue");
    }

    @Override
    public TestSolution checkNodeValue(
            Collection<String> blacklist,
            Collection<String> whitelist, 
            TestSolution testSolution,
            String erroMessageCode) {
        if (whitelist == null) {
            whitelist = new ArrayList<String>();
        }
        if (blacklist == null) {
            blacklist = new ArrayList<String>();
        }

        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {

            TestSolution result = TestSolution.NEED_MORE_INFO;
            boolean isInBlackList = false;
            boolean isInWhiteList = false;
            String nodeValue = workingElement.getTextContent().trim();

            for (String text : blacklist) {
                if (nodeValue.toLowerCase().equals(text.toLowerCase())) {
                    isInBlackList = true;
                    break;
                }
            }
            for (String text : whitelist) {
                if (nodeValue.toLowerCase().equals(text.toLowerCase())) {
                    isInWhiteList = true;
                    break;
                }
            }
            if (isInBlackList && isInWhiteList) {
                throw new RuntimeException(
                        new IncoherentValueDomainsException());
            }
            if (isInBlackList) {
                result = testSolution;
                addSourceCodeRemark(result, workingElement, erroMessageCode,
                        nodeValue);
            }
            if (isInWhiteList) {
                result = TestSolution.PASSED;
            }
//            if (result.equals(TestSolution.NEED_MORE_INFO)) {
//                addSourceCodeRemark(result, workingElement, "VerifyValue",
//                        nodeValue);
//            }
            resultSet.add(result);
        }

        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkTextContentAndAttributeValue(String attributeName,
            Collection<String> blacklist, Collection<String> whitelist) {
        if (whitelist == null) {
            whitelist = new ArrayList<String>();
        }
        if (blacklist == null) {
            blacklist = new ArrayList<String>();
        }

        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.NEED_MORE_INFO;
            boolean isInWhiteList = false;
            boolean isInBlackList = false;
            String textContent = workingElement.getTextContent();
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            for (String text : blacklist) {
                if (textContent.toLowerCase().equals(text.toLowerCase())) {
                    isInBlackList = true;
                    addSourceCodeRemark(result, workingElement,
                            "BlackListedValue", textContent);
                    break;
                }
                if (attribute != null) {
                    if (attribute.getNodeValue().toLowerCase().equals(
                            text.toLowerCase())) {
                        isInBlackList = true;
                        addSourceCodeRemark(result, workingElement,
                                "BlackListedValue", attributeName);
                        break;
                    }
                }
            }
            for (String text : whitelist) {
                if (textContent.toLowerCase().equals(text.toLowerCase())) {
                    isInWhiteList = true;
                    break;
                }
                if (attribute != null) {
                    if (attribute.getNodeValue().toLowerCase().equals(
                            text.toLowerCase())) {
                        isInWhiteList = true;
                        break;
                    }
                }
            }
            if (isInBlackList && isInWhiteList) {
                throw new RuntimeException(
                        new IncoherentValueDomainsException());
            }
            if (isInWhiteList) {
                result = TestSolution.PASSED;
            }
            if (isInBlackList) {
                result = TestSolution.FAILED;
            }
            if (result.equals(TestSolution.NEED_MORE_INFO)) {
                addSourceCodeRemark(result, workingElement, "VerifyValue",
                        attributeName);
            }
            resultSet.add(result);
        }

        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkTextContentValue(Collection<String> blacklist,
            Collection<String> whitelist) {

        if (whitelist == null) {
            whitelist = new ArrayList<String>();
        }
        if (blacklist == null) {
            blacklist = new ArrayList<String>();
        }

        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.NEED_MORE_INFO;
            boolean isInBlackList = false;
            boolean isInWhiteList = false;
            String textContent = workingElement.getTextContent();
            for (String text : blacklist) {
                if (textContent.toLowerCase().equals(text.toLowerCase())) {
                    isInBlackList = true;
                    break;
                }
            }
            for (String text : whitelist) {
                if (textContent.toLowerCase().equals(text.toLowerCase())) {
                    isInWhiteList = true;
                    break;
                }
            }
            if (isInBlackList && isInWhiteList) {
                throw new RuntimeException(
                        new IncoherentValueDomainsException());
            }
            if (isInBlackList) {
                result = TestSolution.FAILED;
                addSourceCodeRemark(result, workingElement, "BlackListedValue",
                        textContent);
            }
            if (isInWhiteList) {
                result = TestSolution.PASSED;
            }
            if (result.equals(TestSolution.NEED_MORE_INFO)) {
                addSourceCodeRemark(result, workingElement, "VerifyValue",
                        textContent);
            }
            resultSet.add(result);
        }

        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkTextContentValueLengthLower(int length,
            TestSolution defaultFailResult) {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            String textContent = workingElement.getTextContent();
            if (textContent.length() > length) {
                result = defaultFailResult;
                addSourceCodeRemark(result, workingElement, "LengthTooLong",
                        textContent);
            }
            resultSet.add(result);
        }

        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public TestSolution checkTextContentValueNotEmpty() {
        Set<TestSolution> resultSet = new HashSet<TestSolution>();
        for (Node workingElement : selectedElementList) {
            TestSolution result = TestSolution.PASSED;
            if (workingElement.getTextContent().length() == 0) {
                result = TestSolution.FAILED;
                addSourceCodeRemark(result, workingElement, "ValueEmpty",
                        workingElement.getNodeValue());
            }
            resultSet.add(result);
        }
        return RuleHelper.synthesizeTestSolutionCollection(resultSet);
    }

    @Override
    public DOMHandler excludeNodesWithAttribute(String attributeName) {
        List<Node> nodes = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute == null) {
                nodes.add(workingElement);
            }
        }
        selectedElementList = nodes;
        return this;
    }

    @Override
    public DOMHandler excludeNodesWithChildNode(ArrayList<String> childNodeNames) {
        List<Node> nodes = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList nodeList = workingElement.getChildNodes();
            boolean found = false;
            for (int i = 0; i < childNodeNames.size() && !found; i++) {
                String childNodeName = childNodeNames.get(i);
                for (int j = 0; j < nodeList.getLength(); j++) {
                    if (nodeList.item(j).getNodeName().equalsIgnoreCase(
                            childNodeName)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                nodes.add(workingElement);
            }
        }
        selectedElementList = nodes;
        return this;
    }

    @Override
    public DOMHandler excludeNodesWithChildNode(String childNodeName) {
        List<Node> nodes = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList nodeList = workingElement.getChildNodes();
            boolean found = false;
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i).getNodeName().equalsIgnoreCase(
                        childNodeName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                nodes.add(workingElement);
            }
        }
        selectedElementList = nodes;
        return this;
    }

    @Override
    public List<String> getAttributeValues(String attributeName) {
        List<String> values = new ArrayList<String>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute != null) {
                values.add(attribute.getNodeValue());
            }
        }
        return values;
    }

    protected int getNodeIndex(Node node) {
        NodeList nodeList = document.getElementsByTagName(node.getNodeName());
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node current = nodeList.item(i);
            if (current.equals(node)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public WebResource getPage() {
        return this.ssp.getPage();
    }

    @Override
    public Collection<ProcessRemark> getRemarkList() {
        return processRemarkService.getRemarkList();
    }

    @Override
    public List<Node> getSelectedElementList() {
        return selectedElementList;
    }

    @Override
    public SSP getSSP() {
        return ssp;
    }

    @Override
    public List<String> getTextContentValues() {
        List<String> values = new ArrayList<String>();
        for (Node workingElement : selectedElementList) {
            values.add(workingElement.getTextContent());
        }
        return values;
    }

    private void initialize() {
        if (initialized) {
            return;
        }
        XPathFactory xPathfactory = XPathFactory.newInstance();
        xpath = xPathfactory.newXPath();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            //@TODO verify the namespace property is necessary in our context
//            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(ssp.getDOM().getBytes("UTF-8")));
            initialized = true;
        } catch (IOException ex) {
            Logger.getLogger(DOMHandlerImpl.class.getName()).log(Level.SEVERE,
                    null, ex);
            throw new RuntimeException(ex);
        } catch (SAXException ex) {
            Logger.getLogger(DOMHandlerImpl.class.getName()).log(Level.SEVERE,
                    null, ex);
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(DOMHandlerImpl.class.getName()).log(Level.SEVERE,
                    null, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean isSelectedElementsEmpty() {
        return selectedElementList.isEmpty();
    }

    @Override
    public DOMHandler keepNodesWithAttribute(String attributeName) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute != null) {
                elements.add(workingElement);
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithAttributeValueEquals(String attributeName,
            Collection<String> values) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute == null) {
                continue;
            }
            for (String value : values) {
                if (attribute.getNodeValue().equalsIgnoreCase(value)) {
                    elements.add(workingElement);
                    break;
                }
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithAttributeValueNonEmpty(String attributeName) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute != null && attribute.getNodeValue().length() > 0) {
                elements.add(workingElement);
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithAttributeValueStartingWith(
            String attributeName, Collection<String> values) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute == null) {
                continue;
            }
            for (String value : values) {
                if (attribute.getNodeValue().toLowerCase().startsWith(
                        value.toLowerCase())) {
                    elements.add(workingElement);
                    break;
                }
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithAttributeValueStartingWith(
            String attributeName, String value) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(
                    attributeName);
            if (attribute == null) {
                continue;
            }
            if (attribute.getNodeValue().toLowerCase().startsWith(
                    value.toLowerCase())) {
                elements.add(workingElement);
                break;
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithChildNode(String childNodeName) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList nodeList = workingElement.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeName().equalsIgnoreCase(childNodeName)) {
                    elements.add(workingElement);
                }
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithoutChildNode(
            Collection<String> childNodeNames) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList nodeList = workingElement.getChildNodes();
            boolean found = false;
            for (int i = 0; i < nodeList.getLength() && !found; i++) {
                Node node = nodeList.item(i);
                for (String childNodeName : childNodeNames) {
                    if (node.getNodeName().equalsIgnoreCase(childNodeName)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                elements.add(workingElement);
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler keepNodesWithoutChildNode(String childNodeName) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList nodeList = workingElement.getChildNodes();
            boolean found = false;
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeName().equalsIgnoreCase(childNodeName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                elements.add(workingElement);
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler selectAttributeByName(String name) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            Node attribute = workingElement.getAttributes().getNamedItem(name);
            if (attribute != null) {
                elements.add(attribute);
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler selectChildNodes(Collection<String> childNodeNames) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList childNodes = workingElement.getChildNodes();
            boolean found = false;
            for (int i = 0; i < childNodes.getLength() && !found; i++) {
                Node childNode = childNodes.item(i);
                for (String childNodeName : childNodeNames) {
                    if (childNode.getNodeName().equalsIgnoreCase(childNodeName)) {
                        elements.add(childNode);
                        found = true;
                        break;
                    }
                }
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler selectChildNodes(String childNodeName) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList childNodes = workingElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                if (childNode.getNodeName().equalsIgnoreCase(childNodeName)) {
                    elements.add(childNode);
                }
            }
        }
        selectedElementList = elements;
        return this;
    }

    @Override
    public DOMHandler selectChildNodesRecursively(
            Collection<String> childNodeNames) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList childNodes = workingElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                elements.addAll(selectChildNodesRecursively(childNodeNames,
                        childNodes.item(i)));
            }
        }
        selectedElementList = elements;
        return this;
    }

    protected Collection<Node> selectChildNodesRecursively(
            Collection<String> childNodeNames, Node node) {// XXX
        List<Node> nodes = new ArrayList<Node>();
        for (String childNodeName : childNodeNames) {
            if (node.getNodeName().equalsIgnoreCase(childNodeName)) {
                nodes.add(node);
                break;
            }
        }
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            nodes.addAll(selectChildNodesRecursively(childNodeNames, childNodes.item(i)));
        }
        return nodes;
    }

    @Override
    public DOMHandler selectChildNodesRecursively(String childNodeName) {
        List<Node> elements = new ArrayList<Node>();
        for (Node workingElement : selectedElementList) {
            NodeList childNodes = workingElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                elements.addAll(selectChildNodesRecursively(childNodeName,
                        childNodes.item(i)));
            }
        }
        selectedElementList = elements;
        return this;
    }

    protected Collection<Node> selectChildNodesRecursively(
            String childNodeName, Node node) {// XXX
        Collection<Node> nodes = new ArrayList<Node>();
        if (node.getNodeName().equalsIgnoreCase(childNodeName)) {
            nodes.add(node);
        }
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            nodes.addAll(selectChildNodesRecursively(childNodeName, childNodes.item(i)));
        }
        return nodes;
    }

    protected Collection<Node> selectChildNodeWithAttributeRecursively(
            String attributeName, Node node) {// XXX
        Collection<Node> nodes = new ArrayList<Node>();
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            Node attribute = attributes.getNamedItem(attributeName);
            if (attribute != null) {
                nodes.add(node);
            }
        }
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            nodes.addAll(selectChildNodeWithAttributeRecursively(attributeName,
                    childNodes.item(i)));
        }
        return nodes;
    }

    @Override
    public DOMHandler selectDocumentNodes(Collection<String> labels) {
        for (String label : labels) {
            NodeList nodeList = document.getElementsByTagName(label);
            for (int j = 0; j < nodeList.getLength(); j++) {
                selectedElementList.add(nodeList.item(j));
            }
        }
        return this;
    }

    @Override
    public DOMHandler selectDocumentNodes(String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        for (int j = 0; j < nodeList.getLength(); j++) {
            selectedElementList.add(nodeList.item(j));
        }
        return this;
    }

    @Override
    public DOMHandler selectDocumentNodesWithAttribute(String attributeName) {
        List<Node> elements = new ArrayList<Node>();
        NodeList childNodes = document.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            elements.addAll(selectChildNodeWithAttributeRecursively(
                    attributeName, childNodes.item(i)));
        }
        selectedElementList = elements;
        return this;
    }

    /**
     * http://www.ibm.com/developerworks/library/x-javaxpathapi.html
     *
     * @param expr
     * @return
     */
    @Override
    public DOMHandler xPathSelectNodeSet(String expr) {
        try {
            XPathExpression xPathExpression = xpath.compile(expr);
            Object result = xPathExpression.evaluate(document,
                    XPathConstants.NODESET);
            NodeList nodeList = (NodeList) result;
            for (int j = 0; j < nodeList.getLength(); j++) {
                selectedElementList.add(nodeList.item(j));
            }
        } catch (XPathExpressionException ex) {
            Logger.getLogger(DOMHandlerImpl.class.getName()).log(Level.SEVERE,
                    null, ex);
            throw new RuntimeException(ex);
        }
        return this;
    }

    @Override
    public void setSelectedElementList(List<Node> selectedElementList) {
        this.selectedElementList = selectedElementList;
    }

    @Override
    public void setSSP(SSP ssp) {
        this.ssp = ssp;
        initialized = false;
    }

    /**
     * This method checks whether an attribute only contains
     * non alphanumeric characters
     * @param attribute
     * @param node
     * @param currentTestSolution
     * @return
     */
    @Override
    public  TestSolution checkAttributeOnlyContainsNonAlphanumericCharacters(
            Node attribute, 
            Node workingElement,
            TestSolution testSolution,
            String remarkMessage) {
        String attributeContent;
        if (attribute.getNodeName().equalsIgnoreCase("#text")) {
            attributeContent = attribute.getTextContent().toLowerCase();
        } else {
            attributeContent = attribute.getNodeValue().toLowerCase();
        }
        if (NON_ALPHANUMERIC_PATTERN.matcher(attributeContent).matches()) {
            addSourceCodeRemark(
                testSolution,
                workingElement,
                remarkMessage,
                attribute.getNodeName());
            return testSolution;
        } else {
            return TestSolution.PASSED;
        }
    }

    /**
     * This method checks whether an attribute only contains
     * non alphanumeric characters
     * @param attribute
     * @param node
     * @param currentTestSolution
     * @return
     */
    @Override
    public  TestSolution checkAttributeOnlyContainsNonAlphanumericCharacters(
            String attributeContent,
            Node workingElement,
            TestSolution testSolution,
            String remarkMessage) {
        processRemarkService.addEvidenceElement("href");
        if (NON_ALPHANUMERIC_PATTERN.matcher(attributeContent).matches()) {
            addSourceCodeRemark(
                testSolution,
                workingElement,
                remarkMessage,
                attributeContent);
            return testSolution;
        } else {
            return TestSolution.PASSED;
        }
    }

    @Override
    public int getSelectedElementNumber() {
        return selectedElementList.size();
    }

    @Override
    public void setProcessRemarkService(ProcessRemarkService processRemarkService) {
        this.processRemarkService = processRemarkService;
    }

}