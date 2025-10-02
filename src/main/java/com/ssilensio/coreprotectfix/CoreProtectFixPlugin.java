package com.ssilensio.coreprotectfix;

import org.bukkit.plugin.java.JavaPlugin;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.Level;

public final class CoreProtectFixPlugin extends JavaPlugin {

    private Path handledErrorLog;
    private final Object logLock = new Object();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeHandledErrorLog();
        printBanner();

        // 1) Villager/ZombieVillager profession shim
        if (getConfig().getBoolean("affect-villagers", true) ||
            getConfig().getBoolean("affect-zombie-villagers", true)) {
            getServer().getPluginManager().registerEvents(
                    new FixVillagerProfessionShim(this), this);
            getLogger().info("[CoreProtectFix] Villager/ZombieVillager profession shim enabled.");
        }

        // 2) Chat bridge for hybrids without Paper AsyncChatEvent
        if (getConfig().getBoolean("chat-bridge-enabled", true)) {
            ChatBridge bridge = new ChatBridge(this);
            bridge.registerIfNeeded();
        }

        getLogger().info("[CoreProtectFix] Enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CoreProtectFix] Disabled.");
    }

    /* Utility */
    public boolean debug() {
        return getConfig().getBoolean("debug", false);
    }

    void logHandledError(String source, Throwable error) {
        if (handledErrorLog == null || error == null) {
            return;
        }

        synchronized (logLock) {
            try {
                Document document = loadLogDocument();
                Element root = document.getDocumentElement();

                Element entry = document.createElement("error");
                entry.setAttribute("timestamp", Instant.now().toString());

                Element sourceElement = document.createElement("source");
                sourceElement.appendChild(document.createTextNode(source));
                entry.appendChild(sourceElement);

                Element typeElement = document.createElement("type");
                typeElement.appendChild(document.createTextNode(error.getClass().getName()));
                entry.appendChild(typeElement);

                Element messageElement = document.createElement("message");
                messageElement.appendChild(document.createTextNode(
                        error.getMessage() == null ? "" : error.getMessage()));
                entry.appendChild(messageElement);

                StringWriter stackWriter = new StringWriter();
                stackWriter.write(error.toString());
                stackWriter.write(System.lineSeparator());
                for (StackTraceElement element : error.getStackTrace()) {
                    stackWriter.write("    at ");
                    stackWriter.write(element.toString());
                    stackWriter.write(System.lineSeparator());
                }
                Throwable cause = error.getCause();
                while (cause != null) {
                    stackWriter.write("Caused by: ");
                    stackWriter.write(cause.toString());
                    stackWriter.write(System.lineSeparator());
                    for (StackTraceElement element : cause.getStackTrace()) {
                        stackWriter.write("    at ");
                        stackWriter.write(element.toString());
                        stackWriter.write(System.lineSeparator());
                    }
                    cause = cause.getCause();
                }

                Element stackElement = document.createElement("stacktrace");
                stackElement.appendChild(document.createCDATASection(stackWriter.toString()));
                entry.appendChild(stackElement);

                root.appendChild(entry);
                saveLogDocument(document);
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException e) {
                getLogger().log(Level.WARNING, "[CoreProtectFix] Failed to append handled error log entry.", e);
            }
        }
    }

    private void initializeHandledErrorLog() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            handledErrorLog = getDataFolder().toPath().resolve("handled-errors.xml");

            DocumentBuilderFactory factory = secureFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();

            if (Files.exists(handledErrorLog)) {
                try (InputStream in = Files.newInputStream(handledErrorLog)) {
                    builder.parse(in);
                    return; // Existing log is valid
                } catch (SAXException ex) {
                    // Fall through to recreate the document
                }
            }

            Document document = builder.newDocument();
            Element root = document.createElement("errors");
            document.appendChild(root);
            saveLogDocument(document);
        } catch (IOException | ParserConfigurationException | TransformerException e) {
            handledErrorLog = null;
            getLogger().log(Level.WARNING, "[CoreProtectFix] Failed to initialize handled error log.", e);
        }
    }

    private Document loadLogDocument() throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = secureFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();

        if (Files.notExists(handledErrorLog)) {
            Document document = builder.newDocument();
            Element root = document.createElement("errors");
            document.appendChild(root);
            return document;
        }

        try (InputStream inputStream = Files.newInputStream(handledErrorLog)) {
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            return document;
        }
    }

    private void saveLogDocument(Document document) throws TransformerException, IOException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            transformerFactory.setAttribute("indent-number", 2);
        } catch (RuntimeException ignored) {
            // Some TransformerFactory implementations do not support this attribute.
        }
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());

        try (Writer writer = Files.newBufferedWriter(handledErrorLog,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            transformer.transform(new DOMSource(document), new StreamResult(writer));
        }
    }

    private DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException ignored) {
            // Older JVMs may not support this feature; continue with defaults.
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (RuntimeException ignored) {
            // Attributes are optional; ignore if unsupported.
        }
        return factory;
    }

    private void printBanner() {
        String[] bannerLines = new String[] {
                "\u001B[36m   ██████╗  ██████╗ ██████╗ ███████╗██████╗ ██████╗ ███████╗████████╗\u001B[0m",
                "\u001B[36m   ██╔══██╗██╔════╝██╔═══██╗██╔════╝██╔══██╗██╔══██╗██╔════╝╚══██╔══╝\u001B[0m",
                "\u001B[36m   ██████╔╝██║     ██║   ██║█████╗  ██████╔╝██████╔╝█████╗     ██║   \u001B[0m",
                "\u001B[36m   ██╔══██╗██║     ██║   ██║██╔══╝  ██╔══██╗██╔══██╗██╔══╝     ██║   \u001B[0m",
                "\u001B[36m   ██║  ██║╚██████╗╚██████╔╝███████╗██║  ██║██║  ██║███████╗   ██║   \u001B[0m",
                "\u001B[36m   ╚═╝  ╚═╝ ╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝   ╚═╝   \u001B[0m",
                "\u001B[35m      CoreProtectFix — bridging CoreProtect with modern chat APIs\u001B[0m"
        };

        for (String line : bannerLines) {
            getLogger().info(line);
        }
    }
}
