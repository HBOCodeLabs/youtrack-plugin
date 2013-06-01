package org.jenkinsci.plugins.youtrack;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.youtrack.youtrackapi.BuildBundle;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;

/**
 * Updates build bundle.
 */
public class YouTrackBuildUpdater extends Recorder {

    private String name;
    private String bundleName;
    private boolean markFixedIfUnstable;
    private boolean onlyAddIfHasFixedIssues;
    private boolean runSilently;

    @DataBoundConstructor
    public YouTrackBuildUpdater(String name, String bundleName, boolean markFixedIfUnstable, boolean onlyAddIfHasFixedIssues, boolean runSilently) {
        this.name = name;
        this.bundleName = bundleName;
        this.markFixedIfUnstable = markFixedIfUnstable;
        this.onlyAddIfHasFixedIssues = onlyAddIfHasFixedIssues;
        this.runSilently = runSilently;
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public boolean isMarkFixedIfUnstable() {
        return markFixedIfUnstable;
    }

    public void setMarkFixedIfUnstable(boolean markFixedIfUnstable) {
        this.markFixedIfUnstable = markFixedIfUnstable;
    }

    public boolean isOnlyAddIfHasFixedIssues() {
        return onlyAddIfHasFixedIssues;
    }

    public void setOnlyAddIfHasFixedIssues(boolean onlyAddIfHasFixedIssues) {
        this.onlyAddIfHasFixedIssues = onlyAddIfHasFixedIssues;
    }

    public boolean isRunSilently() {
        return runSilently;
    }

    public void setRunSilently(boolean runSilently) {
        this.runSilently = runSilently;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        YouTrackSite youTrackSite = YouTrackSite.get(build.getProject());
        if (youTrackSite == null || !youTrackSite.isPluginEnabled()) {
            return true;
        }


        YouTrackSaveFixedIssues action = build.getAction(YouTrackSaveFixedIssues.class);

        //Return early if there is no build to be added
        if(onlyAddIfHasFixedIssues) {
            if(action == null) {
                return true;
            }
            if(action.getIssueIds().isEmpty()) {
                return true;
            }
        }

        YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
        User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
        if(user == null) {
            listener.getLogger().println("FAILED: to log in to youtrack");
            return true;
        }
        EnvVars environment = build.getEnvironment(listener);
        String buildName;
        if(getName() == null || getName().equals("")) {
            buildName = String.valueOf(build.getNumber());
        } else {

            buildName = String.valueOf(build.getNumber()) + " (" + environment.expand(name) + ")";

        }
        String inputBundleName =environment.expand(getBundleName());

        boolean addedBuild = youTrackServer.addBuildToBundle(user, inputBundleName, buildName);
        if(addedBuild) {
            listener.getLogger().println("Added build " + buildName + " to bundle: " + inputBundleName);
        } else {
            listener.getLogger().println("FAILED: adding build " + buildName + " to bundle: " + inputBundleName);
            return true;
        }

        if(action != null) {
            List<String> issueIds = action.getIssueIds();
            boolean stable = build.getResult().isBetterOrEqualTo(Result.SUCCESS);
            boolean unstable = build.getResult().isBetterOrEqualTo(Result.UNSTABLE);


            if(stable || (isMarkFixedIfUnstable() && unstable)) {

                for (String issueId : issueIds) {
                Issue issue = new Issue(issueId);

                    boolean success = youTrackServer.applyCommand(user, issue, "Fixed in build " + buildName, null, null, !runSilently);
                    if(success) {
                        listener.getLogger().println("Updated Fixed in build to " + buildName + " for " + issueId);
                    } else {
                        listener.getLogger().println("FAILED: updating Fixed in build to " + buildName + " for " + issueId);
                    }
                }
            }

        }

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }



        @Override
        public String getDisplayName() {
            return "YouTrack Build Updater";
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(YouTrackBuildUpdater.class, formData);
        }

        public AutoCompletionCandidates doAutoCompleteBundleName(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if(youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if(user != null) {
                    List<BuildBundle> bundles = youTrackServer.getBuildBundles(user);
                    for (BuildBundle bundle : bundles) {
                        if(bundle.getName().toLowerCase().contains(value.toLowerCase())) {
                            autoCompletionCandidates.add(bundle.getName());
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }


    }
}
