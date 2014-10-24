package org.jenkinsci.plugins.youtrack;

import com.google.common.collect.Lists;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.scm.ChangeLogSet;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.Project;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class ExecuteCommandActionTest {
    @Test
    public void noSite() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("", "", "", ""));


        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        doReturn(loggerStream).when(listener).getLogger();
        doReturn(null).when(commandAction).getYouTrackSite(build);

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        assertThat(outputStream.toString().trim(), is("No site configured"));
    }

    @Test
    public void pluginNotEnabled() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("", "", "", ""));

        YouTrackSite site = new YouTrackSite("test", "test", "test", "test");
        YouTrackServer server = new YouTrackServer("test");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        doReturn(loggerStream).when(listener).getLogger();
        doReturn(site).when(commandAction).getYouTrackSite(build);
        doReturn(server).when(commandAction).getYouTrackServer(site);

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        assertThat(outputStream.toString().trim(), is("Plugin not enabled"));
    }

    @Test
    public void couldNotLogin() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);
        YouTrackServer server = mock(YouTrackServer.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("", "", "", ""));

        YouTrackSite site = new YouTrackSite("test", "test", "test", "test");
        site.setPluginEnabled(true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        EnvVars envVars = new EnvVars();

        doReturn(loggerStream).when(listener).getLogger();
        doReturn(site).when(commandAction).getYouTrackSite(build);
        doReturn(server).when(commandAction).getYouTrackServer(site);
        doReturn(envVars).when(build).getEnvironment(listener);
        doReturn(null).when(server).login("test","test");

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        assertThat(outputStream.toString().trim(), is("User not logged in"));
    }

    @Test
    public void testNoCommands() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);
        YouTrackServer server = mock(YouTrackServer.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("", "", "", ""));

        YouTrackSite site = new YouTrackSite("test", "test", "test", "test");
        site.setPluginEnabled(true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        EnvVars envVars = new EnvVars();

        doReturn(loggerStream).when(listener).getLogger();
        doReturn(site).when(commandAction).getYouTrackSite(build);
        doReturn(server).when(commandAction).getYouTrackServer(site);
        doReturn(envVars).when(build).getEnvironment(listener);
        User user = new User();
        user.setLoggedIn(true);
        doReturn(user).when(server).login("test", "test");

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        assertThat(outputStream.toString().trim(), is("No issues to apply command for"));
    }

    @Test
    public void testIssueTextCommand() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);
        YouTrackServer server = mock(YouTrackServer.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("Fixed", "", "branch/hotfix/YT-1", "This is fixed"));

        YouTrackSite site = new YouTrackSite("test", "test", "test", "test");
        site.setPluginEnabled(true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        EnvVars envVars = new EnvVars();

        doReturn(loggerStream).when(listener).getLogger();
        doReturn(site).when(commandAction).getYouTrackSite(build);
        doReturn(server).when(commandAction).getYouTrackServer(site);
        doReturn(envVars).when(build).getEnvironment(listener);
        YouTrackSaveProjectShortNamesAction projectShortNamesAction = new YouTrackSaveProjectShortNamesAction(Lists.newArrayList(new Project("YT")));
        doReturn(projectShortNamesAction).when(build).getAction(YouTrackSaveProjectShortNamesAction.class);

        User user = new User();
        user.setLoggedIn(true);
        doReturn(user).when(server).login("test", "test");

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        verify(server, times(1)).applyCommand("test", user, new Issue("YT-1"), "Fixed", "This is fixed", null, true);
    }

    @Test
    public void testIssueTextCommandEnv() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);
        YouTrackServer server = mock(YouTrackServer.class);
        ChangeLogSet changeLogSet = mock(ChangeLogSet.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("Fixed", "", "${YOUTRACK_CHANGES}", "This is fixed"));

        YouTrackSite site = new YouTrackSite("test", "test", "test", "test");
        site.setPluginEnabled(true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        EnvVars envVars = new EnvVars();

        doReturn(loggerStream).when(listener).getLogger();
        doReturn(changeLogSet).when(build).getChangeSet();
        doReturn(site).when(commandAction).getYouTrackSite(build);
        doReturn(server).when(commandAction).getYouTrackServer(site);
        doReturn(Lists.newArrayList(new YouTrackSCMListenerTest.MockEntry("#XYZ-123 Fixed\nbla bla bla")).iterator()).when(changeLogSet).iterator();
        doReturn(envVars).when(build).getEnvironment(listener);


        YouTrackSaveProjectShortNamesAction projectShortNamesAction = new YouTrackSaveProjectShortNamesAction(Lists.newArrayList(new Project("XYZ")));
        doReturn(projectShortNamesAction).when(build).getAction(YouTrackSaveProjectShortNamesAction.class);

        User user = new User();
        user.setLoggedIn(true);
        doReturn(user).when(server).login("test", "test");

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        verify(server, times(1)).applyCommand("test", user, new Issue("XYZ-123"), "Fixed", "This is fixed", null, true);
    }

    @Test
    public void testSearchCommand() throws IOException, InterruptedException {
        AbstractBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);
        YouTrackServer server = mock(YouTrackServer.class);


        ExecuteCommandAction commandAction = spy(new ExecuteCommandAction("Version: 2", "Version: 1", "", "Upgrade versions"));

        YouTrackSite site = new YouTrackSite("test", "test", "test", "test");
        site.setPluginEnabled(true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream loggerStream = new PrintStream(outputStream);
        EnvVars envVars = new EnvVars();
        User user = new User();
        user.setLoggedIn(true);
        ArrayList<Project> yt = Lists.newArrayList(new Project("YT"));
        YouTrackSaveProjectShortNamesAction projectShortNamesAction = new YouTrackSaveProjectShortNamesAction(yt);
        ArrayList<Issue> foundIssues = Lists.newArrayList(new Issue("YT-2"), new Issue("YT-3"));

        doReturn(loggerStream).when(listener).getLogger();
        doReturn(site).when(commandAction).getYouTrackSite(build);
        doReturn(server).when(commandAction).getYouTrackServer(site);
        doReturn(foundIssues).when(server).search(user, "Version: 1");
        doReturn(envVars).when(build).getEnvironment(listener);
        doReturn(projectShortNamesAction).when(build).getAction(YouTrackSaveProjectShortNamesAction.class);

        doReturn(user).when(server).login("test", "test");

        boolean perform = commandAction.perform(build, launcher, listener);

        assertThat(perform, is(true));
        verify(server, times(1)).applyCommand("test", user, new Issue("YT-2"), "Version: 2", "Upgrade versions", null, true);
        verify(server, times(1)).applyCommand("test", user, new Issue("YT-3"), "Version: 2", "Upgrade versions", null, true);

        verify(build, times(1)).addAction(any(Action.class));

    }

}