package org.jenkinsci.plugins.youtrack;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.youtrack.youtrackapi.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * Associates a YouTrack server and enables the users to set integration settings.
 */
public class YouTrackProjectProperty extends JobProperty<AbstractProject<?, ?>> {
    /**
     * The name of the site.
     */
    private String siteName;

    /**
     * If the YouTrack plugin is enabled.
     */
    private boolean pluginEnabled;
    /**
     * If ping back comments is enabled.
     */
    private boolean commentsEnabled;
    /**
     * If executing commands is enabled.
     */
    private boolean commandsEnabled;
    /**
     * If the commands should be run as the vcs user.
     */
    private boolean runAsEnabled;

    /**
     * If ChangeLog annotations is enabled.
     */
    private boolean annotationsEnabled;

    /**
     * The name of the group comment links should be visible for.
     */
    private String linkVisibility;
    /**
     * Name of state field to check for weather an issue is selected.
     */
    private String stateFieldName;
    /**
     * Comma-separated list of values that are seen as fixed.
     */
    private String fixedValues;
    /**
     * Execute commands silently, i.e. do not notify watchers.
     */
    private boolean silentCommands;

    /**
     * Execute link comment silently.
     */
    private boolean silentLinks;

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();


    @DataBoundConstructor
    public YouTrackProjectProperty(String siteName, boolean pluginEnabled, boolean commentsEnabled, boolean commandsEnabled, boolean runAsEnabled, boolean annotationsEnabled, String linkVisibility, String stateFieldName, String fixedValues, boolean silentCommands, boolean silentLinks) {
        this.siteName = siteName;
        this.pluginEnabled = pluginEnabled;
        this.commentsEnabled = commentsEnabled;
        this.commandsEnabled = commandsEnabled;
        this.runAsEnabled = runAsEnabled;
        this.annotationsEnabled = annotationsEnabled;
        this.linkVisibility = linkVisibility;
        this.stateFieldName = stateFieldName;
        this.fixedValues = fixedValues;
        this.silentCommands = silentCommands;
        this.silentLinks = silentLinks;
    }

    @Override
    public JobPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public boolean isSilentLinks() {
        return silentLinks;
    }

    public void setSilentLinks(boolean silentLinks) {
        this.silentLinks = silentLinks;
    }

    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    public void setPluginEnabled(boolean pluginEnabled) {
        this.pluginEnabled = pluginEnabled;
    }

    public boolean isCommentsEnabled() {
        return commentsEnabled;
    }

    public void setCommentsEnabled(boolean commentsEnabled) {
        this.commentsEnabled = commentsEnabled;
    }

    public boolean isCommandsEnabled() {
        return commandsEnabled;
    }

    public void setCommandsEnabled(boolean commandsEnabled) {
        this.commandsEnabled = commandsEnabled;
    }

    public boolean isRunAsEnabled() {
        return runAsEnabled;
    }

    public void setRunAsEnabled(boolean runAsEnabled) {
        this.runAsEnabled = runAsEnabled;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public boolean isAnnotationsEnabled() {
        return annotationsEnabled;
    }

    public void setAnnotationsEnabled(boolean annotationsEnabled) {
        this.annotationsEnabled = annotationsEnabled;
    }

    public String getLinkVisibility() {
        return linkVisibility;
    }

    public void setLinkVisibility(String linkVisibility) {
        this.linkVisibility = linkVisibility;
    }

    public boolean isSilentCommands() {
        return silentCommands;
    }

    public void setSilentCommands(boolean silentCommands) {
        this.silentCommands = silentCommands;
    }

    public String getStateFieldName() {
        return stateFieldName;
    }

    public void setStateFieldName(String stateFieldName) {
        this.stateFieldName = stateFieldName;
    }

    public String getFixedValues() {
        return fixedValues;
    }

    public void setFixedValues(String fixedValues) {
        this.fixedValues = fixedValues;
    }

    public static final class DescriptorImpl extends JobPropertyDescriptor {
        private final CopyOnWriteList<YouTrackSite> sites = new CopyOnWriteList<YouTrackSite>();

        public DescriptorImpl() {
            super(YouTrackProjectProperty.class);
            load();

        }

        public void setSites(YouTrackSite site) {
            sites.add(site);
        }

        public YouTrackSite[] getSites() {
            return sites.toArray(new YouTrackSite[0]);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            YouTrackProjectProperty ypp = req.bindParameters(YouTrackProjectProperty.class, "youtrack.");
            if (ypp.siteName == null) {
                ypp = null;
            }
            return ypp;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            sites.replaceBy(req.bindParametersToList(YouTrackSite.class,
                    "youtrack."));
            save();
            return true;
        }


        @Override
        public String getDisplayName() {
            return "YouTrack Plugin";
        }

        public FormValidation doVersionCheck(@QueryParameter final String value) throws IOException, ServletException {
            return new FormValidation.URLCheck() {

                @Override
                protected FormValidation check() throws IOException, ServletException {
                    YouTrackServer youTrackServer = new YouTrackServer(value);
                    String[] version = youTrackServer.getVersion();
                    if(version == null) {
                        return FormValidation.warning("Could not get version, maybe because version is below 4.x");
                    } else {
                        return FormValidation.ok();
                    }
                }
            }.check();
        }

        public FormValidation doTestConnection(
                @QueryParameter("youtrack.url") final String url,
                @QueryParameter("youtrack.username") final String username,
                @QueryParameter("youtrack.password") final String password) {

            YouTrackServer youTrackServer = new YouTrackServer(url);
            if (username != null && !username.equals("")) {
                User login = youTrackServer.login(username, password);
                if(login != null) {
                    return FormValidation.ok("Connection ok!");
                } else {
                    return FormValidation.error("Could not login with given options");
                }
            } else {
                return FormValidation.ok();
            }
        }


        public AutoCompletionCandidates doAutoCompleteLinkVisibility(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if(youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if(user != null) {
                    List<Group> groups = youTrackServer.getGroups(user);
                    for (Group group : groups) {
                        if(group.getName().toLowerCase().contains(value.toLowerCase())) {
                            autoCompletionCandidates.add(group.getName());
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }

        public AutoCompletionCandidates doAutoCompleteStateFieldName(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if(youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if(user != null) {
                    List<Field> fields = youTrackServer.getFields(user);
                    for (Field field : fields) {
                        if(field.getName().toLowerCase().contains(value.toLowerCase())) {
                            autoCompletionCandidates.add(field.getName());
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }

        public AutoCompletionCandidates doAutoCompleteFixedValues(@AncestorInPath AbstractProject project,  @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if(youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if(user != null) {
                    StateBundle bundle = youTrackServer.getStateBundleForField(user, youTrackSite.getStateFieldName());
                    if (bundle != null) {
                        for (State state : bundle.getStates()) {
                            if(state.getValue().toLowerCase().contains(value.toLowerCase())) {
                                autoCompletionCandidates.add(state.getValue());
                            }
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }
    }

    public YouTrackSite getSite() {
        YouTrackSite result = null;
        YouTrackSite[] sites = DESCRIPTOR.getSites();
        if (siteName == null && sites.length > 0) {
            result = sites[0];
        }

        for (YouTrackSite site : sites) {
            if (site.getName().equals(siteName)) {
                result = site;
                break;
            }
        }
        if (result != null) {
            result.setPluginEnabled(pluginEnabled);
            result.setCommentEnabled(commentsEnabled);
            result.setCommandsEnabled(commandsEnabled);
            result.setAnnotationsEnabled(annotationsEnabled);
            result.setRunAsEnabled(runAsEnabled);
            result.setLinkVisibility(linkVisibility);
            result.setStateFieldName(stateFieldName);
            result.setFixedValues(fixedValues);
            result.setSilentCommands(silentCommands);
            result.setSilentLinks(silentLinks);
        }
        return result;
    }
}
