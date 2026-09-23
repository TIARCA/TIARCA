package io.mrarm.irc.upnp.rpc;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.mrarm.irc.upnp.UPnPHttpClient;
import io.mrarm.irc.upnp.XMLParseHelper;

public abstract class UPnPRemoteCall {

    public static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";


    protected Document doSend(URL serviceEndpoint) throws IOException, SAXException,
            TransformerException, UPnPRPCError {
        if (!validate())
            throw new InvalidParameterException("Validation of the request failed");
        Document doc = buildDocument();

        Log.d("UPnPRemoteCall", "Request action: " + getSOAPAction());

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter xmlWriter = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(xmlWriter));
        String xmlBodyString = xmlWriter.toString();
        Log.d("UPnPRemoteCall", "Request body: " + xmlBodyString);
        byte[] xmlBodyBytes = xmlBodyString.getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/xml; charset=\"utf-8\"");
        headers.put("SOAPAction", "\"" + getSOAPAction() + "\"");
        UPnPHttpClient.Response response =
                UPnPHttpClient.post(serviceEndpoint, headers, xmlBodyBytes);
        Log.d("UPnPRemoteCall", "Response status: " + response.getStatusCode());

        byte[] responseBytes = response.getBody();
        if (responseBytes.length == 0)
            throw new IOException("Empty UPnP RPC response");
        Log.d("UPnPRemoteCall", "Response: " +
                new String(responseBytes, StandardCharsets.UTF_8));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document ret;
        try {
            ret = factory.newDocumentBuilder().parse(new ByteArrayInputStream(responseBytes));
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        if (!ret.getDocumentElement().getLocalName().equals("Envelope"))
            throw new IOException("Invalid reply");
        Element body = XMLParseHelper.findChildElement(ret.getDocumentElement(), "Body");
        Element fault = XMLParseHelper.findChildElement(body, "Fault");
        if (fault != null)
            throw new UPnPRPCError(fault);
        return ret;
    }

    protected abstract boolean validate();

    protected abstract String getSOAPAction();


    /* XML building */

    public Document buildDocument() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc;
        try {
            doc = factory.newDocumentBuilder().getDOMImplementation()
                    .createDocument(NS_SOAP, "s:Envelope", null);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        doc.getDocumentElement().setAttribute("s:encodingStyle",
                "http://schemas.xmlsoap.org/soap/encoding/");
        buildEnvelope(doc.getDocumentElement());
        return doc;
    }


    protected void buildEnvelope(Element envelope) {
        Document document = envelope.getOwnerDocument();
        Element body = document.createElementNS(NS_SOAP, "s:Body");
        envelope.appendChild(body);
        buildBody(body);
    }

    protected void buildBody(Element body) {
        Document document = body.getOwnerDocument();
        Element request = createRequest(document);
        body.appendChild(request);
    }

    protected abstract Element createRequest(Document document);


    protected static Element addArgumentNode(Element container, String name, String value) {
        Element ret = container.getOwnerDocument().createElement(name);
        ret.setTextContent(value);
        container.appendChild(ret);
        return ret;
    }

}
