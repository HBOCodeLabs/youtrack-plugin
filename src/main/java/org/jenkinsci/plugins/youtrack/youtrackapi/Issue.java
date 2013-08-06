package org.jenkinsci.plugins.youtrack.youtrackapi;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This object represents an issue.
 */
public class Issue {
    /**
     * The id of the issue.
     */
    private String id;
    /**
     * The state of the issue.
     */
    private String state;

    /**
     * Constructs an issue object with the given id.
     *
     * @param id id of issue.
     */
    public Issue(String id) {
        this.id = id;
    }

    /**
     * @return the id of the issue.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the state of the issue.
     *
     * @return the state of the issue.
     */
    public String getState() {
        return state;
    }

    /**
     * Parses data of an issue request. It only parses the state field.
     */
    public static class IssueHandler extends DefaultHandler {
        /**
         * Field currently being parsed.
         */
        private String currentField;
        /**
         * Holder for character data.
         */
        private StringBuilder stringBuilder = new StringBuilder();
        /**
         * Holder for the result.
         */
        private Issue issue;

        /**
         * State field name.
         */
        private String stateFieldName;


        public IssueHandler(String stateFieldName) {
            this.stateFieldName = stateFieldName;
            if(stateFieldName == null || stateFieldName.equals("")) {
                this.stateFieldName = "State";
            }
        }

        /**
         * The resulting issue object.
         *
         * @return the issue.
         */
        public Issue getIssue() {
            return issue;
        }

        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            stringBuilder.setLength(0);
            if (qName.equals("issue")) {
                this.issue = new Issue(attributes.getValue("id"));
            }
            if (qName.equals("field")) {
                currentField = attributes.getValue("name");
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            stringBuilder.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            if (qName.equals("value")) {
                if (currentField.equals(stateFieldName)) {
                    issue.state = stringBuilder.toString();
                }
            }
        }
    }
}
