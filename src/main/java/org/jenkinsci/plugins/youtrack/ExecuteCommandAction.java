package org.jenkinsci.plugins.youtrack;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.youtrack.youtrackapi.*;
import org.jenkinsci.plugins.youtrack.youtrackapi.Project;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is command for executing arbitrary commands on issues.
 */
public class ExecuteCommandAction extends Builder {
    @Getter
    @Setter
    private String command;
    @Getter
    @Setter
    private String search;
    @Getter
    @Setter
    private String issueInText;
    @Getter
    @Setter
    private String comment;

    @DataBoundConstructor
    public ExecuteCommandAction(String command, String search, String issueInText, String comment) {
        this.command = command;
        this.search = search;
        this.issueInText = issueInText;
        this.comment = comment;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        YouTrackSite youTrackSite = YouTrackSite.get(build.getProject());
        if (youTrackSite != null) {
            if (youTrackSite.isPluginEnabled()) {
                EnvVars environment = build.getEnvironment(listener);
                String searchQuery = environment.expand(search);
                String commandToExecute = environment.expand(command);

                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if (user != null && user.isLoggedIn()) {
                    Set<Issue> issues = new HashSet<Issue>();
                    if (StringUtils.isNotBlank(searchQuery)) {
                        issues.addAll(youTrackServer.search(user, searchQuery));
                    }
                    if (StringUtils.isNotBlank(issueInText)) {
                        issues.addAll(findIssuesInText(build, environment, issueInText));
                    }
                    List<Command> appliedCommands = new ArrayList<Command>();
                    String expandedComment = environment.expand(comment);
                    for (Issue issue : issues) {
                        Command appliedCommand = youTrackServer.applyCommand(youTrackSite.getName(), user, issue, commandToExecute, expandedComment, null, true);
                        appliedCommands.add(appliedCommand);
                    }
                    if (!appliedCommands.isEmpty()) {
                        YouTrackCommandAction youTrackCommandAction = build.getAction(YouTrackCommandAction.class);
                        if (youTrackCommandAction == null) {
                            youTrackCommandAction = new YouTrackCommandAction(build);
                            build.addAction(youTrackCommandAction);
                        }
                        for (Command appliedCommand : appliedCommands) {
                            youTrackCommandAction.addCommand(appliedCommand);
                        }
                    }
                    if (issues.isEmpty()) {
                        listener.getLogger().println("No issues to apply command for");
                    }
                } else {
                    listener.getLogger().println("User not logged in");
                }
            } else {
                listener.getLogger().print("Plugin not enabled");
            }
        } else {
            listener.getLogger().println("No site configured");
        }
        return true;
    }

    private List<Issue> findIssuesInText(AbstractBuild<?, ?> build, EnvVars environment, String issueInText) {
        String textToSearchForIssues = environment.expand(issueInText);
        YouTrackSaveProjectShortNamesAction projectShortNamesAction = build.getAction(YouTrackSaveProjectShortNamesAction.class);
        if (projectShortNamesAction != null) {
            return findIssuesIds(projectShortNamesAction.getShortNames(), textToSearchForIssues);
        }
        return new ArrayList<Issue>();
    }

    private List<Issue> findIssuesIds(List<String> projects, String issueText) {
        ArrayList<Issue> issues = new ArrayList<Issue>();
        String projectIds = StringUtils.join(projects, "|");
        Pattern projectPattern = Pattern.compile("(" + projectIds + "-" + "(\\d+)" + ")");
        Matcher matcher = projectPattern.matcher(issueText);
        while (matcher.find()) {
            if (matcher.groupCount() >= 1) {
                String issueId = matcher.group(1);
                issues.add(new Issue(issueId));
            }
        }
        return issues;
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Execute YouTrack Command";
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(ExecuteCommandAction.class, formData);
        }

        @SuppressWarnings("UnusedDeclaration")
        public AutoCompletionCandidates doAutoCompleteSearch(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if (youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if (user != null) {
                    List<Suggestion> suggestions = youTrackServer.searchSuggestions(user, value);
                    for (Suggestion suggestion : suggestions) {
                        if (suggestion.getCompletionStart() == 0) {
                            String completeSuggestion = emptyIfNull(suggestion.getPrefix()) + suggestion.getOption() + emptyIfNull(suggestion.getSuffix());
                            autoCompletionCandidates.add(completeSuggestion);
                        } else {
                            String validValue = value.substring(0, suggestion.getCompletionStart());
                            String completeSuggestion = emptyIfNull(suggestion.getPrefix()) + suggestion.getOption() + emptyIfNull(suggestion.getSuffix());
                            autoCompletionCandidates.add(validValue + completeSuggestion);
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }


        public String emptyIfNull(String text) {
            if (text == null) return "";
            return text;
        }
    }

}
