package org.jenkinsci.plugins.youtrack;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.youtrack.youtrackapi.*;
import org.jenkinsci.plugins.youtrack.youtrackapi.Project;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Post-build step to create an issue in Youtrack if the build fails.
 */
public class YoutrackCreateIssueOnBuildFailure extends Notifier {
    @Getter @Setter private String project;
    @Getter @Setter private String summary;
    @Getter @Setter private String description;
    @Getter @Setter private String threshold;
    @Getter @Setter private String visibility;
    @Getter @Setter private String command;
    @Getter @Setter private boolean attachBuildLog;

    public static final String FAILURE = "failure";

    public static final String FAILUREORUNSTABL = "failureOrUnstable";

    @DataBoundConstructor
    public YoutrackCreateIssueOnBuildFailure(String project, String summary, String description, String threshold, String visibility, String command, boolean attachBuildLog) {
        this.project = project;
        this.summary = summary;
        this.description = description;
        this.threshold = threshold;
        this.visibility = visibility;
        this.command = command;
        this.attachBuildLog = attachBuildLog;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        YouTrackSite youTrackSite = getYouTrackSite(build);
        if (youTrackSite == null) {
            listener.getLogger().println("No YouTrack site configured");
            return true;
        }

        if (shouldCreateIssue(build)) {
            YouTrackServer server = getYouTrackServer(youTrackSite);
            User user = server.login(youTrackSite.getUsername(), youTrackSite.getPassword());
            if (user == null) {
                listener.getLogger().println("Could not login user to YouTrack");
                return true;
            }

            EnvVars environment = build.getEnvironment(listener);
            String title = environment.expand(this.summary);
            String description = environment.expand(this.description);
            String command = environment.expand(this.command);

            if (title == null || "".equals(title)) {
                title = "Build failure in build " + build.getNumber();
            } else {
                title = environment.expand(title);
            }
            if (description == null || "".equals(description)) {
                description = getAbsoluteUrl(build);
            } else {
                description = environment.expand(description);
            }

            File buildLog = null;
            if (attachBuildLog) {
                buildLog = build.getLogFile();
            }
            Command issue = server.createIssue(youTrackSite.getName(), user, project, title, description, command, buildLog);
            YouTrackCommandAction youTrackCommandAction = build.getAction(YouTrackCommandAction.class);
            if (youTrackCommandAction == null) {
                youTrackCommandAction = new YouTrackCommandAction(build);
                build.addAction(youTrackCommandAction);
            }
            youTrackCommandAction.addCommand(issue);

            listener.getLogger().println("Created new YouTrack issue " + issue.getIssueId());
        }

        return true;

    }

    public String getAbsoluteUrl(AbstractBuild<?, ?> build) {
        return build.getAbsoluteUrl();
    }

    YouTrackServer getYouTrackServer(YouTrackSite youTrackSite) {
        return new YouTrackServer(youTrackSite.getUrl());
    }

    YouTrackSite getYouTrackSite(AbstractBuild<?, ?> build) {
        return YouTrackSite.get(build.getProject());
    }


    private boolean shouldCreateIssue(AbstractBuild<?, ?> build) {
        Result result = build.getResult();
        if (FAILURE.equals(threshold) && result.isBetterThan(Result.FAILURE)) {
            return false;
        } else if (FAILUREORUNSTABL.equals(threshold) && result.isBetterThan(Result.UNSTABLE)) {
            return false;
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return org.jenkinsci.plugins.youtrack.Messages.create_issue_on_build_failure_header();
        }


        @Override
        public Publisher newInstance(final StaplerRequest req, final JSONObject formData) {
            return req.bindJSON(YoutrackCreateIssueOnBuildFailure.class, formData);
        }


        public FormValidation doCheckProject(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public AutoCompletionCandidates doAutoCompleteProject(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if(youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if(user != null) {
                    List<Project> groups = youTrackServer.getProjects(user);
                    for (Project youtrackProject : groups) {
                        if(youtrackProject.getShortName().toLowerCase().contains(value.toLowerCase())) {
                            autoCompletionCandidates.add(youtrackProject.getShortName());
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }

    }
}
