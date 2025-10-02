package com.ssilensio.coreprotectfix;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class HandledErrorLogger {

    private static final String LOG_FILE_NAME = "handled-errors.xml";
    private static final String ROOT_ELEMENT = "errors";
    private static final String ENTRY_ELEMENT = "error";

    private final Path logFile;
    private final Logger logger;
    private final DocumentBuilderFactory factory;
    private final Object lock = new Object();

    static HandledErrorLogger tryCreate(Path dataDirectory, Logger logger) {
        try {
            return new HandledErrorLogger(dataDirectory, logger);
        } catch (IOException | ParserConfigurationException | TransformerException exception) {
            logger.log(Level.WARNING, "[CoreProtectFix] Failed to initialize handled error log.", exception);
            return null;
        }
    }

    private HandledErrorLogger(Path dataDirectory, Logger logger)
            throws IOException, ParserConfigurationException, TransformerException {
        this.logger = logger;
        Files.createDirectories(dataDirectory);
        this.logFile = dataDirectory.resolve(LOG_FILE_NAME);
        this.factory = createSecureFactory();
        ensureLogDocument();
    }

    void append(String source, Throwable error) {
        if (error == null) {
            return;
        }

        synchronized (lock) {
            try {
                Document document = loadDocument();
                Element root = ensureRoot(document);
                Element entry = createEntry(document, source, error);
                root.appendChild(entry);
                writeDocument(document);
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException exception) {
                logger.log(Level.WARNING, "[CoreProtectFix] Failed to append handled error log entry.", exception);
            }
        }
    }

    private void ensureLogDocument() throws IOException, ParserConfigurationException, TransformerException {
        DocumentBuilder builder = newDocumentBuilder();
        if (Files.exists(logFile)) {
            try (InputStream in = Files.newInputStream(logFile)) {
                builder.parse(in);
                return;
            } catch (SAXException exception) {
                // Continue and recreate the document when the existing one is invalid.
            }
        }

        Document document = builder.newDocument();
        document.appendChild(document.createElement(ROOT_ELEMENT));
        writeDocument(document);
    }

    private Document loadDocument() throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilder builder = newDocumentBuilder();
        if (Files.notExists(logFile)) {
            Document document = builder.newDocument();
            document.appendChild(document.createElement(ROOT_ELEMENT));
            return document;
        }

        try (InputStream inputStream = Files.newInputStream(logFile)) {
            Document document = builder.parse(inputStream);
            Element root = document.getDocumentElement();
            if (root == null || !ROOT_ELEMENT.equals(root.getNodeName())) {
                Document fallback = builder.newDocument();
                fallback.appendChild(fallback.createElement(ROOT_ELEMENT));
                return fallback;
            }
            root.normalize();
            return document;
        }
    }

    private Element ensureRoot(Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            root = document.createElement(ROOT_ELEMENT);
            document.appendChild(root);
        }
        return root;
    }

    private Element createEntry(Document document, String source, Throwable error) {
        Element entry = document.createElement(ENTRY_ELEMENT);
        entry.setAttribute("timestamp", Instant.now().toString());
        entry.appendChild(createTextElement(document, "source", safe(source)));
        entry.appendChild(createTextElement(document, "type", error.getClass().getName()));
        entry.appendChild(createTextElement(document, "message", safe(error.getMessage())));

        CDATASection stackTrace = document.createCDATASection(stackTrace(error));
        Element stackElement = document.createElement("stacktrace");
        stackElement.appendChild(stackTrace);
        entry.appendChild(stackElement);
        return entry;
    }

    private void writeDocument(Document document) throws TransformerException, IOException {
        Transformer transformer = newTransformer();
        try (Writer writer = Files.newBufferedWriter(logFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            transformer.transform(new DOMSource(document), new StreamResult(writer));
        }
    }

    private Element createTextElement(Document document, String tag, String value) {
        Element element = document.createElement(tag);
        element.appendChild(document.createTextNode(value));
        return element;
    }

    private String safe(String value) {
        return Objects.toString(value, "");
    }

    private DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return factory.newDocumentBuilder();
    }

    private DocumentBuilderFactory createSecureFactory() {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException ignored) {
            // Some JVMs do not support this flag; continue without it.
        }
        try {
            documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Optional properties, ignore when unsupported.
        }
        return documentBuilderFactory;
    }

    private Transformer newTransformer() throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException ignored) {
            // Continue if secure processing is unavailable.
        }
        try {
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException ignored) {
            // Optional attributes not supported by all implementations.
        }

        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        return transformer;
    }

    private String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            error.printStackTrace(printWriter);
        }
        return writer.toString();
    }
}
