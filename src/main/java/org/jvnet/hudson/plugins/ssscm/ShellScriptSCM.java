/**
 * The MIT License
 * 
 * Copyright (c) 2011, Richard Sczepczenski
 *
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal 
 * in the Software without restriction, including without limitation the rights 
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies 
 * of the Software, and to permit persons to whom the Software is furnished to do so, 
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.ssscm;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.tasks.Messages;

/**
 * A class to utilize shell scripts as an SCM
 * 
 * @author <a href="mailto:rms27@optonline.net">Richard Sczepczenski</a>
 *
 */
public class ShellScriptSCM extends SCM implements Serializable{


	/**
	 * Default serial version.
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The shell to be used to execute command line programs/scripts.
	 */
	private final String SHELL           = "/bin/sh";
	
	/**
	 * Temporary file name used for executing shell scripts.
	 */
	private final String TEMP_FILE_NAME  = "SSSCM";
	
	/**
	 * The file extension of the temporary file.
	 */
	private final String TEMP_FILE_EXT   = ".sh";
	
	/**
	 * The checkout shell.
	 */
	private String checkoutShell;
	
	/**
	 * The polling shell.
	 */
	private String pollingShell;
	
	/**
	 * Configuration option: Set to true to use the checkout shell for polling.  The
	 * default is false.
	 */
	private boolean useCheckoutForPolling;

	/**
	 * Creates the ShellScriptSCM.
	 * 
	 * @param checkoutShell 
	 *      The shell command used when a checkout is done.
	 * @param pollingShell 
	 *      The shell command to use for polling.
	 */
	public ShellScriptSCM(String checkoutShell, String pollingShell) {
		this.checkoutShell = checkoutShell;
		this.pollingShell  = pollingShell;
		this.useCheckoutForPolling = false;
	}
	

	/**
	 *  Creates the ShellScriptSCM.
	 *  
	 * @param checkoutShell 
	 *      The shell command used when a checkout is done. 
	 *      
	 * @param pollingShell 
	 *      The shell command to use for polling.
	 *      
	 * @param useCheckoutForPolling 
	 *      Set to true to use the checkout shell for polling, false
	 *      if the polling shell is to be used for polling.
	 */
	@DataBoundConstructor
	public ShellScriptSCM(String checkoutShell, String pollingShell, Boolean useCheckoutForPolling) {
		this.checkoutShell = checkoutShell;
		this.pollingShell  = pollingShell;
		this.useCheckoutForPolling = useCheckoutForPolling.booleanValue();		
	}

	/**
	 * Returns the shell which is currently being used for 'checkout' operations.
	 * 
	 * @return 
	 *      The shell command used when a checkout is done.
	 */
	@Exported
	public String getCheckoutShell() {
		return checkoutShell;
	}

	/**
	 * Set the shell to be used when a 'checkout' is requested.
	 * 
	 * @param checkoutShell 
	 *      The shell command used when a checkout is done.
	 */
	@Exported
	public void setCheckoutShell(String checkoutShell) {
		this.checkoutShell = checkoutShell;
	}

	/**
	 * Returns the shell currently being used for polling operations.
	 * 
	 * @return 
	 *      The polling shell.
	 */
	@Exported
	public String getPollingShell() {
		return pollingShell;
	}

	/**
	 * Set the shell to be used for polling.
	 * 
	 * @param pollingShell 
	 *      The shell command to use for polling.
	 */
	@Exported
	public void setPollingShell(String pollingShell) {
		this.pollingShell = pollingShell;
	}

	/**
	 * Get the state of the conditional for using the checkout shell for polling operations.
	 * 
	 * @return 
	 *      True if the checkout shell is to be used for polling, false if the
	 *      polling shell is to be used for polling.
	 */
	@Exported
	public boolean isUseCheckoutForPolling() {
		return useCheckoutForPolling;
	}

	/**
	 * Set the conditional for using the checkout shell for polling.
	 * 
	 * @param useCheckoutForPolling 
	 *      Set to true to use the checkout shell for polling, false
	 *      if the polling shell is to be used for polling.
	 */
	@Exported
	public void setUseCheckoutForPolling(Boolean useCheckoutForPolling) {
		this.useCheckoutForPolling = useCheckoutForPolling.booleanValue();
	}

	/**
	 * Checkout is performed by using the specified 'checkout' shell.
	 */
	@Override
	public boolean checkout(AbstractBuild<?,?> build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {

		this.execute(checkoutShell, launcher,workspace,listener);

		return true;
	}

	/**
	 * This method is not implemented.
	 */
	@Override
	public ChangeLogParser createChangeLogParser() {
		// TODO Auto-generated method stub
		return null;
	}

	
	/**
	 * This method executes the polling shell in order to determine if there
	 * are any changes.  If the polling shell exits with exit code '1' then 
	 * this method will return true to indicate that a checkout must be done. 
	 * Any other exit code will not trigger a checkout.
	 */
	@Override
	public boolean pollChanges(AbstractProject<?,?> project, Launcher launcher,
			FilePath workspace, TaskListener listener) throws IOException,
			InterruptedException {
		int rc = 0;

		if( useCheckoutForPolling ){
			rc = this.execute(checkoutShell, launcher,workspace,listener);
		} else {
			rc = this.execute(pollingShell, launcher,workspace,listener);			
		}
		
		// Only return true if the return code from the shell command is 1 
		if(rc == 1 ){
			return true;			
		} else {
			return false;
		}
		
	}

	/**
	 * This method is not implemented.
	 */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build,
			Launcher launcher, TaskListener listener) throws IOException,
			InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}


	/**
	 * This method is not implemented.
	 */
	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}


	
	/* (non-Javadoc)
	 * @see hudson.scm.SCM#getDescriptor()
	 */
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

	/**
	 * Helper method to execute a shell command.
	 * 
	 * @param shellCmd 
	 *      The shell command to be executed.
	 *      
	 * @param launcher 
	 *      The process launcher.
	 *      
	 * @param workspace 
	 *      The workspace where the shell command is going to be executed.
	 *      
	 * @param listener 
	 *      The TaskListener
	 *      
	 * @return 
	 *      The execution status of the shell command.
	 *      
	 * @throws IOException
	 *      If there is an exception during the shell command execution.
	 *      
	 * @throws InterruptedException
	 *      If there is an exception during the shell command execution.
	 */
	private int execute(String shellCmd, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
		FilePath script=null;
		try {
			try {
				script = workspace.createTextTempFile(TEMP_FILE_NAME, TEMP_FILE_EXT, shellCmd, false);
			} catch (IOException e) {
				Util.displayIOException(e,listener);
				e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_UnableToProduceScript()));
				return -1;
			}

			int r;
			try {
				r = launcher.launch().cmds(buildCommandLine(shellCmd, script)).envs(System.getenv()).stdout(listener).pwd(workspace).join();
			} catch (IOException e) {
				Util.displayIOException(e,listener);
				e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_CommandFailed()));
				r = -1;
			}
			return r;
		} finally {
			try {
				if(script!=null)
					script.delete();
			} catch (IOException e) {
				Util.displayIOException(e,listener);
				e.printStackTrace( listener.fatalError(Messages.CommandInterpreter_UnableToDelete(script)) );
			}
		}

	}
	
	/**
	 * 
	 * @param shellCmd
	 * @param script
	 * @return
	 */
    private String[] buildCommandLine(String shellCmd, FilePath script) {
        if(shellCmd.startsWith("#!")) {
            // interpreter override
            int end = shellCmd.indexOf('\n');
            if(end<0)   end=shellCmd.length();
            List<String> args = new ArrayList<String>();
            args.addAll(Arrays.asList(Util.tokenize(shellCmd.substring(0,end).trim())));
            args.add(script.getRemote());
            args.set(0,args.get(0).substring(2));   // trim off "#!"
            return args.toArray(new String[args.size()]);
        } else
            return new String[] { SHELL,"-xe",script.getRemote()};
    }

	
	/**
     * Descriptor for {@link ShellScriptSCM}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/SSSCM/sssscm/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends SCMDescriptor<ShellScriptSCM> implements hudson.model.ModelObject {

        public DescriptorImpl() {
			super(ShellScriptSCM.class, null);
			load();
		}
    	
		@Override
		public String getDisplayName() {
			return "Shell Script";
		}
		
		@Override
		public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
	        // Save configuration
            save();

            return super.configure(req, json);
 		}
	
    }
	
}
