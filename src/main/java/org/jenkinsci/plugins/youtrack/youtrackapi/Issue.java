package org.jenkinsci.plugins.youtrack.youtrackapi;

import lombok.Getter;
import lombok.Setter;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * This object represents an issue.
 */
public class Issue {
    /**
     * The id of the issue.
     */
    @Getter @Setter private String id;
    /**
     * The state of the issue.
     */
    @Getter @Setter private String state;

    /**
     * Title of issue.
     */
    @Getter @Setter private String summary;

    @Getter @Setter private String resolved;

    /**
     * Summary of issue.
     */
    @Getter @Setter private String description;

    /**
     * Constructs an issue object with the given id.
     *
     * @param id id of issue.
     */
    public Issue(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Issue issue = (Issue) o;

        if (id != null ? !id.equals(issue.id) : issue.id != null) return false;

        return true;
    }

    @Override
    public String toString() {
        return "Issue{" +
                "id='" + id + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
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
        @Getter private Issue issue;

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
                } else if (currentField.equals("summary")) {
                    issue.summary = stringBuilder.toString();
                } else if (currentField.equals("description")) {
                    issue.description = stringBuilder.toString();
                } else if (currentField.equals("resolved")) {
                    issue.resolved = stringBuilder.toString();
                }
            }
        }
    }

    public static class IssueSearchHandler extends DefaultHandler {
        @Getter
        private List<Issue> issueList = new ArrayList<Issue>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (qName.equals("issue")) {
                String issueId = attributes.getValue("id");
                issueList.add(new Issue(issueId));
            }
        }
    }

    public static class IssueSearchSuggestionHandler extends DefaultHandler {
        @Getter
        private List<Suggestion> suggestions = new ArrayList<Suggestion>();

        private String prefix;
        private String suffix;
        private String option;
        private String description;
        private int completionStart;
        private int completionEnd;
        private int matchStart;
        private int matchEnd;

        private StringBuilder stringBuilder = new StringBuilder();
        private boolean inItem;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            stringBuilder.setLength(0);
            if (qName.equals("item")) {
                inItem = true;
            } else if(qName.equals("completion")) {
                completionStart = Integer.parseInt(attributes.getValue("start"));
                completionEnd = Integer.parseInt(attributes.getValue("end"));
            } else if (qName.equals("match")) {
                matchStart = Integer.parseInt(attributes.getValue("start"));
                matchEnd = Integer.parseInt(attributes.getValue("end"));
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            stringBuilder.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            if (qName.equals("item")) {
                inItem = false;
                Suggestion suggestion = new Suggestion();
                suggestion.setOption(option);
                suggestion.setDescription(description);
                suggestion.setSuffix(suffix);
                suggestion.setPrefix(prefix);
                suggestion.setCompletionStart(completionStart);
                suggestion.setCompletionEnd(completionEnd);
                suggestion.setMatchStart(matchStart);
                suggestion.setMatchEnd(matchEnd);

                suggestions.add(suggestion);
            } else if (qName.equals("suffix")) {
                suffix = stringBuilder.toString();
            } else if (qName.equals("option")) {
                option = stringBuilder.toString();
            } else if (qName.equals("description")) {
                description = stringBuilder.toString();
            } else if (qName.equals("prefix")) {
                prefix = stringBuilder.toString();
            }
        }
    }
}
