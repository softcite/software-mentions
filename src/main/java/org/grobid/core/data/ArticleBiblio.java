package org.grobid.core.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

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
     * Convert this MetadataArticle to JSON string for API response
     */
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder json = new StringBuilder();

        json.append("\"biblio\": {");
        boolean firstField = true;

        // Add DOI if available
        if (doi != null && !doi.trim().isEmpty()) {
            if (!firstField) json.append(", ");
            try {
                json.append("\"doi\": ").append(mapper.writeValueAsString(doi));
            } catch (JsonProcessingException e) {
                json.append("\"doi\": \"\"");
            }
            firstField = false;
        }

        // Add title if available
        if (title != null && !title.trim().isEmpty()) {
            if (!firstField) json.append(", ");
            try {
                json.append("\"title\": ").append(mapper.writeValueAsString(title));
            } catch (JsonProcessingException e) {
                json.append("\"title\": \"\"");
            }
            firstField = false;
        }

        // Add authors if available
        if (StringUtils.isNotBlank(authors)) {
            if (!firstField) json.append(", ");
            json.append("\"authors\": \"" + authors + "\"");
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

            String title = extractTitle(teiDocument, xpath);
            String doi = extractDOI(teiDocument, xpath);
            List<String> authors = extractAuthors(teiDocument, xpath);

            ArticleBiblio articleMetadata = new ArticleBiblio();
            articleMetadata.setDoi(doi);
            articleMetadata.setTitle(title);

            if (CollectionUtils.isNotEmpty(authors)) {
                articleMetadata.setAuthors(String.join(", ", authors));
            }

            return articleMetadata.hasContent() ? Optional.of(articleMetadata) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String extractTitle(org.w3c.dom.Document doc, XPath xpath) {
        try {
            NodeList titleNodes = (NodeList) xpath.evaluate("//teiHeader/fileDesc/titleStmt/title[@level='a'][@type='main']/text()", doc, XPathConstants.NODESET);
            if (titleNodes != null && titleNodes.getLength() > 0) {
                String title = titleNodes.item(0).getNodeValue().trim();
                return title.isEmpty() ? "" : title;
            }
        } catch (Exception e) {
        }
        return "";
    }

    private static String extractDOI(org.w3c.dom.Document doc, XPath xpath) {
        try {
            NodeList doiNodes = (NodeList) xpath.evaluate("//teiHeader/fileDesc/sourceDesc/biblStruct/idno[2]/text()", doc, XPathConstants.NODESET);
            if (doiNodes != null && doiNodes.getLength() > 0) {
                String doi = doiNodes.item(0).getNodeValue().trim();
                return doi.isEmpty() ? "" : doi;
            }
        } catch (Exception e) {
        }
        return "";
    }

    private static List<String> extractAuthors(org.w3c.dom.Document doc, XPath xpath) {
        List<String> authors = new ArrayList<>();
        try {
            NodeList authorNodes = (NodeList) xpath.evaluate("//teiHeader/fileDesc/sourceDesc/biblStruct/analytic/author/persName", doc, XPathConstants.NODESET);
            for (int i = 0; i < authorNodes.getLength(); i++) {
                String author = formatAuthorFromNode(authorNodes.item(i));
                if (!author.isEmpty() && !authors.contains(author)) {
                    authors.add(author);
                }
            }
        } catch (Exception e) {
        }
        return authors;
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
            String lastName = author.getLastName();
            String firstName = author.getFirstName();
            String middleName = author.getMiddleName();

            // Build name parts as "first middle last"
            StringBuilder fullName = new StringBuilder();

            if (firstName != null && !firstName.trim().isEmpty()) {
                fullName.append(firstName.trim());
            }

            if (middleName != null && !middleName.trim().isEmpty()) {
                if (fullName.length() > 0) {
                    fullName.append(" ");
                }
                fullName.append(middleName.trim());
            }

            if (lastName != null && !lastName.trim().isEmpty()) {
                if (fullName.length() > 0) {
                    fullName.append(" ");
                }
                fullName.append(lastName.trim());
            }

            formattedAuthors.append(fullName);
        }
        return formattedAuthors.toString();
    }

    /**
     * Format author from XML node as "surname, name"
     */
    private static String formatAuthorFromNode(org.w3c.dom.Node node) {
        if (node == null) return "";

        if (node.getNodeName().equals("persName")) {
            String surname = "";
            String forename = "";

            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                org.w3c.dom.Node child = childNodes.item(i);
                if (child.getNodeName().equals("surname")) {
                    surname = child.getTextContent().trim();
                } else if (child.getNodeName().equals("forename")) {
                    forename = child.getTextContent().trim();
                }
            }

            if (!surname.isEmpty()) {
                return forename.isEmpty() ? surname : surname + ", " + forename;
            }
        }

        return node.getTextContent().trim();
    }

    @Override
    public String toString() {
        return String.format("MetadataArticle{doi='%s', title='%s', authors=%s}",
            doi, title, authors);
    }
}