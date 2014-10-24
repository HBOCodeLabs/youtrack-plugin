package org.jenkinsci.plugins.youtrack.youtrackapi;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.jenkinsci.plugins.youtrack.Command;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class contains methods for communication with a YouTrack server using the REST API for version 4 of YouTrack.
 */
public class YouTrackServer {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = Logger.getLogger(YouTrackServer.class.getName());
    /**
     * The url of the YouTrack server.
     */
    private final String serverUrl;

    private static String getErrorMessage(InputStream errorStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream));
        String l;
        StringBuilder stringBuilder = new StringBuilder();
        while ((l = bufferedReader.readLine()) != null) {
            stringBuilder.append(l).append("\n");
        }
        try {
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();
            ErrorHandler errorHandler = new ErrorHandler();
            saxParser.parse(new InputSource(new StringReader(stringBuilder.toString())), errorHandler);
            return errorHandler.errorMessage;
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.WARNING, "Could not parse error response", e);
        } catch (SAXException e) {
            LOGGER.log(Level.WARNING, "Could not parse error response", e);
        }

        // If we couldn't parse the body, return the raw response.
        return stringBuilder.toString();
    }

    /**
     * Constructs a server.
     *
     * @param serverUrl the url of the server.
     */
    public YouTrackServer(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public Command createIssue(String siteName, User user, String project, String title, String description, String command, File attachment) {
        return createIssuePOST(siteName, user, project, title, description, command, attachment);
    }

    private Command createIssuePOST(String siteName, User user, String project, String title, String description, String command, File attachment) {
        Command cmd = new Command();
        cmd.setCommand("[Create issue]");
        cmd.setDate(new Date());
        cmd.setSiteName(siteName);

        if (user == null || !user.isLoggedIn()) {
            cmd.setStatus(Command.Status.NOT_LOGGED_IN);
            return null;
        }

        cmd.setStatus(Command.Status.FAILED);
        try {
            String params = "project="+URLEncoder.encode(project, "UTF-8")+"&summary="+URLEncoder.encode(title, "UTF-8")+"&description=" + URLEncoder.encode(description, "UTF-8");

            // Against documentation. This call is supposed to be PUT, but only POST is working.
            PostMethod postMethod = new PostMethod(serverUrl + "/rest/issue");

            for (String cookie : user.getCookies()) {
                postMethod.addRequestHeader("Cookie", cookie);
            }

            List<Part> parts = new ArrayList<Part>();
            parts.add(new StringPart("project", project, "UTF-8"));
            parts.add(new StringPart("summary", title, "UTF-8"));
            parts.add(new StringPart("description", description, "UTF-8"));
            if(attachment != null) {
                parts.add(new FilePart("attachment", attachment));
            }
            Part[] partsArray = {};
            Part[] array = parts.toArray(partsArray);
            postMethod.setRequestEntity(new MultipartRequestEntity(array, new HttpMethodParams()));

            HttpClient httpClient = new HttpClient();
            int responseCode = httpClient.executeMethod(postMethod);
            // Because we're varying in the POST vs. PUT call, check for a couple possible
            // success responses, though currently I'm only ever seeing 200 returned.
            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(postMethod.getResponseBodyAsStream()));
                StringBuilder stringBuilder = new StringBuilder();
                for (String l = null; (l = bufferedReader.readLine()) != null;) {
                    stringBuilder.append(l).append("\n");
                }

                try {
                    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    CreateIssueHandler handler = new CreateIssueHandler();
                    saxParser.parse(new InputSource(new StringReader(stringBuilder.toString())), handler);
                    String issueId = handler.issueId;

                    LOGGER.log(Level.INFO, "Created issue " + issueId);

                    if (issueId != null) {
                        Issue issue = new Issue(issueId);
                        if (StringUtils.isNotBlank(command)) {
                            applyCommand(siteName, user, issue, command, "", null, false);
                            cmd.setCommand(command);
                        }
                        cmd.setIssueId(issueId);
                    }
                } catch (Exception e) {
                    cmd.setCommand("[Unable to apply command]");
                }

                cmd.setStatus(Command.Status.OK);

                return cmd;
            }

            cmd.setResponse(getErrorMessage(postMethod.getResponseBodyAsStream()));
            LOGGER.log(Level.WARNING, "Did not create issue: " + cmd.getResponse());
        } catch (MalformedURLException e) {
            cmd.setResponse(e.getMessage());
            LOGGER.log(Level.WARNING, "Did not create issue", e);
        } catch (IOException e) {
            cmd.setResponse(e.getMessage());
            LOGGER.log(Level.WARNING, "Did not create issue", e);
        }
        return cmd;
    }


    public List<Group> getGroups(User user) {
        List<Group> groups = new ArrayList<Group>();
        try {
            URL url = new URL(serverUrl + "/rest/admin/group");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();


            for (String cookie : user.getCookies()) {

                urlConnection.setRequestProperty("Cookie", cookie);
            }

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            try {
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    Group.GroupListHandler dh = new Group.GroupListHandler();
                    saxParser.parse(urlConnection.getInputStream(), dh);
                    return dh.getGroups();
                }
            } catch (ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            } catch (SAXException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        }
        return groups;
    }

    /**
     * Gets a state bundle for the given name filled with the state values/
     *
     * @param user            the user
     * @param stateBundleName the name of the state bundle.
     * @return the state bundle.
     */
    public StateBundle getStateBundleWithName(User user, String stateBundleName) {
        try {
            String stateBundleUrl = serverUrl + "/rest/admin/customfield/stateBundle/" + stateBundleName;
            URL url = new URL(stateBundleUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();


            for (String cookie : user.getCookies()) {

                urlConnection.setRequestProperty("Cookie", cookie);
            }

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            try {
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    StateBundle stateBundle = new StateBundle(stateBundleName, stateBundleUrl);
                    StateBundle.StateBundleHandler dh = new StateBundle.StateBundleHandler(stateBundle);
                    saxParser.parse(urlConnection.getInputStream(), dh);
                    return stateBundle;

                }
            } catch (ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            } catch (SAXException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        }
        return null;
    }

    public StateBundle getStateBundleForField(User user, String fieldName) {
        try {
            String fieldUrl = serverUrl + "/rest/admin/customfield/field/" + fieldName;
            URL url = new URL(fieldUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();


            for (String cookie : user.getCookies()) {

                urlConnection.setRequestProperty("Cookie", cookie);
            }

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            try {
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    Field.FieldHandler dh = new Field.FieldHandler(fieldName, fieldUrl);
                    saxParser.parse(urlConnection.getInputStream(), dh);
                    Field field = dh.getField();

                    if (field.getType().equals("state[1]")) {
                        return getStateBundleWithName(user, field.getDefaultBundle());
                    } else {
                        return null;
                    }
                }
            } catch (ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            } catch (SAXException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        }
        return null;
    }

    public List<Field> getFields(User user) {
        List<Field> fields = new ArrayList<Field>();
        try {
            URL url = new URL(serverUrl + "/rest/admin/customfield/field/");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();


            for (String cookie : user.getCookies()) {

                urlConnection.setRequestProperty("Cookie", cookie);
            }

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            try {
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    Field.FieldListHandler dh = new Field.FieldListHandler();
                    saxParser.parse(urlConnection.getInputStream(), dh);
                    return dh.getFields();
                }
            } catch (ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            } catch (SAXException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        }
        return fields;
    }

    /**
     * Gets all projects that the given user can see. The user shall be one obtained from {@link #login(String, String)}, i.e.
     * it should contains the cookie strings for the users login session.
     *
     * @param user the user to get projects for.
     * @return the list of projects the user can see.
     */
    public List<Project> getProjects(User user) {
        try {
            URL url = new URL(serverUrl + "/rest/project/all");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();


            for (String cookie : user.getCookies()) {

                urlConnection.setRequestProperty("Cookie", cookie);
            }

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            try {
                SAXParser saxParser = saxParserFactory.newSAXParser();
                Project.ProjectListHandler dh = new Project.ProjectListHandler();
                saxParser.parse(urlConnection.getInputStream(), dh);
                return dh.getProjects();
            } catch (ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            } catch (SAXException e) {
                LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get YouTrack Projects", e);
        }
        return null;

    }

    /**
     * Adds a comment to the issue with currently logged in user.
     *
     * @param siteName name of site configuration to comment on.
     * @param user     the currently logged in user.
     * @param issue    the issue to comment on.
     * @param comment  the comment text.
     * @param group    the group the comment should be visible to.
     * @param silent   prevents watchers from being notified.
     * @return if comment was added.
     */
    public Command comment(String siteName, User user, Issue issue, String comment, String group, boolean silent) {
        Command command = new Command();
        command.setSiteName(siteName);
        command.setIssueId(issue.getId());
        command.setComment(comment);
        command.setDate(new Date());
        command.setGroup(group);
        command.setSilent(silent);
        if (user == null || !user.isLoggedIn()) {
            command.setStatus(Command.Status.NOT_LOGGED_IN);
        } else {
            command.setStatus(Command.Status.FAILED);
        }
        if (user != null) {
            command.setUsername(user.getUsername());
        }


        try {
            URL url = new URL(serverUrl + "/rest/issue/" + issue.getId() + "/execute");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);

            if (user != null) {
                for (String cookie : user.getCookies()) {
                    urlConnection.setRequestProperty("Cookie", cookie);
                }
            }

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
            outputStreamWriter.write("comment=" + URLEncoder.encode(comment, "UTF-8"));
            if (group != null && !group.equals("")) {
                outputStreamWriter.write("&group=" + group);
            }
            if (silent) {
                outputStreamWriter.write("&disableNotifications=" + true);
            }
            outputStreamWriter.flush();

            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                command.setStatus(Command.Status.OK);
                return command;
            } else {
                command.setStatus(Command.Status.FAILED);
                command.setResponse(getErrorMessage(urlConnection.getErrorStream()));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not comment", e);
            command.setResponse(e.getMessage());
        }
        return command;
    }

    /**
     * Apply a command to an issue.
     *
     * @param user    the user used to apply the command, shall be one with cookies set.
     * @param issue   the issue to apply the command to.
     * @param command the command to apply.
     * @param comment comment with the command, null is allowed.
     * @param runAs   user to apply the command as, null is allowed.
     * @param notify  notifies watchers.
     */
    public Command applyCommand(String siteName, User user, Issue issue, String command, String comment, User runAs, boolean notify) {
        Command cmd = new Command();
        cmd.setCommand(command);
        cmd.setSilent(!notify);
        cmd.setIssueId(issue.getId());
        cmd.setSiteName(siteName);
        cmd.setDate(new Date());
        cmd.setStatus(Command.Status.FAILED);
        cmd.setComment(comment);

        if (user == null || !user.isLoggedIn()) {
            cmd.setStatus(Command.Status.NOT_LOGGED_IN);
            return cmd;
        }
        cmd.setUsername(user.getUsername());
        try {


            URL url = new URL(serverUrl + "/rest/issue/" + issue.getId() + "/execute");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);

            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());


            String str = "command=" + URLEncoder.encode(command, "UTF-8");
            if (comment != null) {
                str += "&comment=" + URLEncoder.encode(comment, "UTF-8");
            }
            if (runAs != null) {
                str += "&runAs=" + runAs.getUsername();
            }
            if (!notify) {
                str += "&disableNotifications=true";
            }
            outputStreamWriter.write(str);
            outputStreamWriter.flush();

            int responseCode = urlConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                cmd.setStatus(Command.Status.OK);
                return cmd;
            }

            cmd.setStatus(Command.Status.FAILED);
            cmd.setResponse(getErrorMessage(urlConnection.getErrorStream()));
            LOGGER.log(Level.WARNING, "Could not apply command: " + cmd.getResponse());
        } catch (IOException e) {
            cmd.setResponse(e.getMessage());
            LOGGER.log(Level.WARNING, "Could not apply command", e);
        }
        return cmd;
    }

    /**
     * Get a YouTrack user from the e-mail address.
     *
     * @param user  the user to get.
     * @param email the email to get.
     * @return the user, null if none found.
     */
    public User getUserByEmail(User user, String email) {
        try {
            URL url = new URL(serverUrl + "/rest/admin/user?q=" + email);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }

            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                SAXParser saxParser = saxParserFactory.newSAXParser();
                User.UserRefHandler dh = new User.UserRefHandler();
                saxParser.parse(urlConnection.getInputStream(), dh);
                return dh.getUser();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get user", e);
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.WARNING, "Could not get user", e);
        } catch (SAXException e) {
            LOGGER.log(Level.WARNING, "Could not get user", e);
        }
        return null;
    }

    /**
     * Logs in a user. The result is the user object with cookies set, which should
     * be used on all subsequent requests.
     *
     * @param username the username of the user.
     * @param password the password of the user.
     * @return user, null if fails to login
     */
    public User login(String username, String password) {

        try {
            User user = new User();
            user.setUsername(username);
            URL url = new URL(serverUrl + "/rest/user/login");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
            outputStreamWriter.write("login=" + username + "&password=" + password);
            outputStreamWriter.flush();

            int responseCode = urlConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Map<String, List<String>> headerFields = urlConnection.getHeaderFields();
                List<String> strings = headerFields.get("Set-Cookie");

                for (String string : strings) {
                    user.getCookies().add(string);
                }
                user.setLoggedIn(true);
                return user;
            } else {

                return user;
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not login", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not login", e);
        }
        return null;
    }

    /**
     * Adds a build with the name to the bundle with the given name.
     *
     * @param siteName   the name of the site to add bundle to.
     * @param user       the logged in user.
     * @param bundleName the name of the bundle to add a build to.
     * @param buildName  the name of the build to add.
     */
    public Command addBuildToBundle(String siteName, User user, String bundleName, String buildName) {
        Command cmd = new Command();
        cmd.setCommand("[Add '" + buildName + "' to " + " '" + bundleName + "']");
        cmd.setDate(new Date());
        cmd.setSiteName(siteName);

        if (user == null || !user.isLoggedIn()) {
            cmd.setStatus(Command.Status.NOT_LOGGED_IN);
            return cmd;
        } else {
            cmd.setStatus(Command.Status.FAILED);
        }
        user.setUsername(user.getUsername());
        try {

            String encode = URLEncoder.encode(bundleName, "ISO-8859-1").replace("+", "%20");
            String encode1 = URLEncoder.encode(buildName, "ISO-8859-1").replace("+", "%20");
            URL url = new URL(serverUrl + "/rest/admin/customfield/buildBundle/" + encode + "/" + encode1);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("PUT");
            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
            outputStreamWriter.flush();


            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                cmd.setStatus(Command.Status.OK);
                return cmd;
            }

            cmd.setStatus(Command.Status.FAILED);
            cmd.setResponse(getErrorMessage(urlConnection.getErrorStream()));
        } catch (MalformedURLException e) {
            cmd.setResponse(e.getMessage());
            LOGGER.log(Level.WARNING, "Could not add to bundle", e);
        } catch (IOException e) {
            cmd.setResponse(e.getMessage());
            LOGGER.log(Level.WARNING, "Could not add to bundle", e);
        }
        return cmd;
    }

    /**
     * Gets an issue by issue id.
     * <p/>
     * Currently the only value retrieved is the State field.
     *
     * @param user    the user session.
     * @param issueId the id of the issue.
     * @return the issue if any.
     */
    public Issue getIssue(User user, String issueId, String stateField) {
        try {
            URL url = new URL(serverUrl + "/rest/issue/" + issueId + "?wikifyDescription=true");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }


            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser saxParser = factory.newSAXParser();
                    Issue.IssueHandler issueHandler = new Issue.IssueHandler(stateField);
                    saxParser.parse(urlConnection.getInputStream(), issueHandler);
                    return issueHandler.getIssue();
                } catch (ParserConfigurationException e) {
                    LOGGER.log(Level.WARNING, "Could not get issue", e);
                } catch (SAXException e) {
                    LOGGER.log(Level.WARNING, "Could not get issue", e);
                }
            }

        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get issue", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get issue", e);
        }
        return null;
    }

    public String[] getVersion() {
        try {
            URL url = new URL(serverUrl + "/rest/workflow/version");
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    VersionHandler versionHandler = new VersionHandler();
                    saxParser.parse(urlConnection.getInputStream(), versionHandler);
                    return versionHandler.version.split(".");
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not get version", e);
            } catch (ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Could not get version", e);
            } catch (SAXException e) {
                LOGGER.log(Level.WARNING, "Could not get version", e);
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Wrong url", e);
        }
        return null;
    }

    public List<BuildBundle> getBuildBundles(User user) {
        try {
            URL url = new URL(serverUrl + "/rest/admin/customfield/buildBundle");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }


            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser saxParser = factory.newSAXParser();
                    BuildBundle.Handler issueHandler = new BuildBundle.Handler();
                    saxParser.parse(urlConnection.getInputStream(), issueHandler);
                    return issueHandler.getBundles();
                } catch (ParserConfigurationException e) {
                    LOGGER.log(Level.WARNING, "Could not get issue", e);
                } catch (SAXException e) {
                    LOGGER.log(Level.WARNING, "Could not get issue", e);
                }
            }

        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not get issue", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get issue", e);
        }
        return null;
    }

    public List<Issue> search(User user, String searchQuery) {
        try {
            URL url = new URL(serverUrl + "/rest/issue?filter=" + URLEncoder.encode(searchQuery, "UTF-8") + "&max=" + Integer.MAX_VALUE);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }


            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser saxParser = factory.newSAXParser();
                    Issue.IssueSearchHandler issueSearchHandler = new Issue.IssueSearchHandler();
                    saxParser.parse(urlConnection.getInputStream(), issueSearchHandler);
                    return issueSearchHandler.getIssueList();
                } catch (ParserConfigurationException e) {
                    LOGGER.log(Level.WARNING, "Could not find issues", e);
                } catch (SAXException e) {
                    LOGGER.log(Level.WARNING, "Could not find issues", e);
                }
            }

        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not find issues", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not find issues", e);
        }
        return null;
    }

    public List<Suggestion> searchSuggestions(User user, String current) {
        try {
            URL url = new URL(serverUrl + "/rest/issue/intellisense?filter=" + URLEncoder.encode(current, "UTF-8"));
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            for (String cookie : user.getCookies()) {
                urlConnection.setRequestProperty("Cookie", cookie);
            }


            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser saxParser = factory.newSAXParser();
                    Issue.IssueSearchSuggestionHandler issueSearchHandler = new Issue.IssueSearchSuggestionHandler();
                    saxParser.parse(urlConnection.getInputStream(), issueSearchHandler);
                    return issueSearchHandler.getSuggestions();
                } catch (ParserConfigurationException e) {
                    LOGGER.log(Level.WARNING, "Could not find issues", e);
                } catch (SAXException e) {
                    LOGGER.log(Level.WARNING, "Could not find issues", e);
                }
            }

        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Could not find issues", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not find issues", e);
        }
        return new ArrayList<Suggestion>();
    }

    private static class VersionHandler extends DefaultHandler {
        boolean inVersion = false;
        private StringBuilder stringBuilder = new StringBuilder();
        private String version;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (qName.equals("version")) {
                inVersion = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            if (inVersion) {
                stringBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.equals("version")) {
                inVersion = false;
                version = stringBuilder.toString();
            }
            super.endElement(uri, localName, qName);
        }


    }

    private static class ErrorHandler extends DefaultHandler {
        private StringBuilder stringBuilder = new StringBuilder();
        private boolean inError;
        private String errorMessage;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (qName.equals("error")) {
                inError = true;
                stringBuilder.setLength(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            if (qName.equals("error")) {
                errorMessage = stringBuilder.toString();
                inError = false;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            if (inError) {
                stringBuilder.append(ch, start, length);
            }
        }
    }

    private static class CreateIssueHandler extends DefaultHandler {
        public String issueId;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.equals("issue")) {
                for (int i = 0; i < attributes.getLength(); ++i) {
                    if (attributes.getQName(i).equals("id")) {
                        issueId = attributes.getValue(i);
                        break;
                    }
                }
            }
        }
    }
}
