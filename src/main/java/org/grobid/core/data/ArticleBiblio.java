package org.grobid.core.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data class to hold article metadata (DOI, title, authors) extracted from documents.
 * This class provides a clean separation from BiblioComponent which is designed
 * for reference components within software mentions.
 */
public class ArticleBiblio {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleBiblio.class);

    private String doi;
    private String title;
    private String authors;

    public ArticleBiblio() {
    }

    public ArticleBiblio(String doi, String title, String authors) {
        this.doi = doi;
        this.title = title;
        this.authors = authors;
    }
    // Getters and setters
    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getAuthors() {
        return this.authors;
    }
    /**
     * Check if this metadata article has any meaningful content
     */
    public boolean hasContent() {
        return (StringUtils.isNotBlank(doi)) ||
            (StringUtils.isNotBlank(title)) ||
            (StringUtils.isNotBlank(authors));
    }

    /**
     * Convert this ArticleBiblio to JSON string for API response
     */
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder json = new StringBuilder();
        boolean hasField = false;

        json.append("\"biblio\": {");

        // Add DOI if available
        if (StringUtils.isNotBlank(doi)) {
            json.append("\"doi\": ");
            try {
                json.append(mapper.writeValueAsString(doi));
            } catch (JsonProcessingException e) {
                json.append("\"\"");
            }
            hasField = true;
        }

        // Add title if available
        if (StringUtils.isNotBlank(title)) {
            if (hasField) json.append(", ");
            json.append("\"title\": ");
            try {
                json.append(mapper.writeValueAsString(title));
            } catch (JsonProcessingException e) {
                json.append("\"\"");
            }
            hasField = true;
        }

        // Add authors if available
        if (StringUtils.isNotBlank(authors)) {
            if (hasField) json.append(", ");
            try {
                json.append("\"authors\": ").append(mapper.writeValueAsString(authors));
            } catch (JsonProcessingException e) {
                json.append("\"authors\": \"\"");
            }
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Create MetadataArticle from BiblioItem
     */
    public static Optional<ArticleBiblio> fromBiblioItem(BiblioItem biblioItem) {
        if (biblioItem == null) {
            LOGGER.debug("BiblioItem is null, cannot create MetadataArticle");
            return Optional.empty();
        }

        LOGGER.debug("Creating MetadataArticle from BiblioItem");
        ArticleBiblio metadata = new ArticleBiblio();

        if (biblioItem.getDOI() != null && !biblioItem.getDOI().trim().isEmpty()) {
            metadata.setDoi(biblioItem.getDOI());
            LOGGER.debug("Extracted DOI: " + biblioItem.getDOI());
        }

        if (biblioItem.getTitle() != null && !biblioItem.getTitle().trim().isEmpty()) {
            metadata.setTitle(biblioItem.getTitle());
            LOGGER.debug("Extracted title: " + biblioItem.getTitle());
        }

        List<Person> authors = biblioItem.getFullAuthors();
        if (CollectionUtils.isNotEmpty(authors)) {
            String authorsAsList = formatAuthors(authors);
            metadata.setAuthors(authorsAsList);
        }

        return metadata.hasContent() ? Optional.of(metadata) : Optional.empty();
    }

    /**
     * Extract article metadata from TEI XML Document using XPath
     */
    public static Optional<ArticleBiblio> fromTeiDocument(org.w3c.dom.Document teiDocument) {
        if (teiDocument == null) {
            return Optional.empty();
        }

        try {
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();

            // Set up namespace context for TEI documents
            NamespaceContext nsContext = new TEINamespaceContext();
            xpath.setNamespaceContext(nsContext);

            // Extract metadata using namespace-aware XPath
            String title = extractTitle(teiDocument, xpath);
            String doi = extractDOI(teiDocument, xpath);
            List<Person> authors = extractAuthors(teiDocument, xpath);

            LOGGER.debug("Extracted from TEI: title='{}', doi='{}', authors={}", title, doi, authors.size());

            ArticleBiblio articleMetadata = new ArticleBiblio();
            articleMetadata.setDoi(doi);
            articleMetadata.setTitle(title);

            if (CollectionUtils.isNotEmpty(authors)) {
                articleMetadata.setAuthors(formatAuthors(authors));
            }

            boolean hasContent = articleMetadata.hasContent();
            LOGGER.debug("Article metadata has content: {}, result: {}", hasContent, articleMetadata);
            return hasContent ? Optional.of(articleMetadata) : Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Error extracting article metadata from TEI document", e);
            return Optional.empty();
        }
    }

    private static String extractTitle(org.w3c.dom.Document doc, XPath xpath) {
        try {
            // Try multiple possible title paths with namespace
            String[] titlePaths = {
                "//tei:teiHeader/tei:fileDesc/tei:titleStmt/tei:title[@level='a'][@type='main']/text()",
                "//tei:teiHeader/tei:fileDesc/tei:titleStmt/tei:title[@level='a']",
                "//tei:teiHeader/tei:fileDesc/tei:titleStmt/tei:title[@type='main']/text()",
            };

            for (String path : titlePaths) {
                try {
                    NodeList titleNodes = (NodeList) xpath.evaluate(path, doc, XPathConstants.NODESET);
                    if (titleNodes != null && titleNodes.getLength() > 0) {
                        String title = titleNodes.item(0).getNodeValue().trim();
                        if (!title.isEmpty()) {
                            LOGGER.debug("Found title using path '{}': '{}'", path, title);
                            return title;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to extract title with path '{}': {}", path, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error extracting title from TEI document", e);
        }
        return "";
    }

    private static String extractDOI(org.w3c.dom.Document doc, XPath xpath) {
        String path = "//tei:teiHeader/tei:fileDesc/tei:sourceDesc/tei:biblStruct/tei:idno[@type='DOI']/text()";

        try {
            NodeList doiNodes = (NodeList) xpath.evaluate(path, doc, XPathConstants.NODESET);
            if (doiNodes != null && doiNodes.getLength() > 0) {
                String doi = doiNodes.item(0).getNodeValue().trim();
                if (!doi.isEmpty() && (doi.startsWith("10.") || doi.contains("doi.org"))) {
                    LOGGER.debug("Found DOI using path '{}': '{}'", path, doi);
                    return doi;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to extract DOI with path '{}': {}", path, e.getMessage());
        }
        return "";
    }

    private static List<Person> extractAuthors(org.w3c.dom.Document doc, XPath xpath) {
        List<Person> authors = new ArrayList<>();
        String authorsPath = "//tei:teiHeader/tei:fileDesc/tei:sourceDesc/tei:biblStruct/tei:analytic/tei:author/tei:persName";

        try {
            NodeList authorNodes = (NodeList) xpath.evaluate(authorsPath, doc, XPathConstants.NODESET);
            LOGGER.debug("Found {} author nodes using path '{}'", authorNodes.getLength(), authorsPath);

            for (int i = 0; i < authorNodes.getLength(); i++) {
                Person person = createPersonFromNode(authorNodes.item(i));
                if (person != null) {
                    authors.add(person);
                    LOGGER.debug("Added author: '{}'", formatPersonName(person));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to extract authors with path '{}': {}", authorsPath, e.getMessage());
        }

        return authors;
    }

    /**
     * Create a Person object from an XML persName node
     */
    private static Person createPersonFromNode(org.w3c.dom.Node node) {
        if (node == null) return null;

        Person person = new Person();

        if (node.getNodeName().equals("persName")) {
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                org.w3c.dom.Node child = childNodes.item(i);
                if (child.getNodeName().equals("surname")) {
                    person.setLastName(child.getTextContent().trim());
                } else if (child.getNodeName().equals("forename")) {
                    person.setFirstName(child.getTextContent().trim());
                } else if (child.getNodeName().equals("middlename")) {
                    person.setMiddleName(child.getTextContent().trim());
                }
            }
        } else {
            // If it's not a persName node, use the text content as the full name
            String fullName = node.getTextContent().trim();
            if (!fullName.isEmpty()) {
                person.setLastName(fullName); // Fallback: put full name in last name
            }
        }

        // Return person if it has any name data
        return (StringUtils.isNotBlank(person.getLastName()) ||
                StringUtils.isNotBlank(person.getFirstName()) ||
                StringUtils.isNotBlank(person.getMiddleName())) ? person : null;
    }

    /**
     * Format list of authors as "first middle last, first2 middle2 last2, ..."
     */
    private static String formatAuthors(List<Person> authors) {
        StringBuilder formattedAuthors = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            if (i > 0) {
                formattedAuthors.append(", ");
            }

            Person author = authors.get(i);
            formattedAuthors.append(formatPersonName(author));
        }
        return formattedAuthors.toString();
    }

    /**
     * Format a Person's name as "first middle last"
     */
    private static String formatPersonName(Person person) {
        String lastName = person.getLastName();
        String firstName = person.getFirstName();
        String middleName = person.getMiddleName();

        StringBuilder fullName = new StringBuilder();

        if (StringUtils.isNotBlank(firstName)) {
            fullName.append(firstName.trim());
        }

        if (StringUtils.isNotBlank(middleName)) {
            if (!fullName.isEmpty()) {
                fullName.append(" ");
            }
            fullName.append(middleName.trim());
        }

        if (StringUtils.isNotBlank(lastName)) {
            if (!fullName.isEmpty()) {
                fullName.append(" ");
            }
            fullName.append(lastName.trim());
        }

        return fullName.toString();
    }

    
    @Override
    public String toString() {
        return String.format("MetadataArticle{doi='%s', title='%s', authors=%s}",
            doi, title, authors);
    }

    /**
     * Simple namespace context for TEI documents
     */
    private static class TEINamespaceContext implements NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            if ("tei".equals(prefix)) {
                return "http://www.tei-c.org/ns/1.0";
            }
            return null;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            if ("http://www.tei-c.org/ns/1.0".equals(namespaceURI)) {
                return "tei";
            }
            return null;
        }

        @Override
        public java.util.Iterator<String> getPrefixes(String namespaceURI) {
            java.util.Set<String> prefixes = new java.util.HashSet<>();
            if ("http://www.tei-c.org/ns/1.0".equals(namespaceURI)) {
                prefixes.add("tei");
            }
            return prefixes.iterator();
        }
    }
}