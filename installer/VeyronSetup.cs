// Veyron.exe: the one file players download (build-installer.ps1). It carries the launcher (Veyron-Launcher.zip, a
// jpackage folder with its own Java) as a resource. The first start, or a start after a newer Veyron.exe, unpacks it to
// %LOCALAPPDATA%\Veyron\launcher and puts a Veyron shortcut on the desktop and in the start menu; then it starts the
// launcher, which keeps the game client up to date by itself (ClientUpdates on the game server).
// C# 5 (the .NET Framework 4 compiler that every Windows has): no string interpolation.
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("Veyron")]
[assembly: AssemblyProduct("Veyron")]

static class VeyronSetup
{
	[STAThread]
	static void Main(string[] args)
	{
		// --install-to <dir>: unpack there only (no shortcut, no start), for testing a build
		bool test = args.Length == 2 && args[0] == "--install-to";
		string root = test ? args[1] : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Veyron");
		string launcherDir = Path.Combine(root, "launcher");
		string exe = Path.Combine(Path.Combine(launcherDir, "Veyron"), "Veyron.exe");
		string versionFile = Path.Combine(root, "version.txt");
		string version = ReadResource("version.txt").Trim();

		bool installed = File.Exists(exe) && File.Exists(versionFile) && File.ReadAllText(versionFile).Trim() == version;
		if (!installed)
		{
			Exception error = null;
			Form form = ProgressForm();
			Thread worker = new Thread(delegate ()
			{
				try { Install(root, launcherDir, versionFile, version); }
				catch (Exception e) { error = e; }
				form.BeginInvoke(new Action(form.Close));
			});
			form.Shown += delegate { worker.Start(); };
			Application.EnableVisualStyles();
			Application.Run(form);
			if (error != null)
			{
				MessageBox.Show("Veyron could not be installed: " + error.Message + "\n\nIf Veyron is already running, close it and try again.",
					"Veyron", MessageBoxButtons.OK, MessageBoxIcon.Error);
				return;
			}
			if (test)
				return;
			try
			{
				Shortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Veyron.lnk"), exe);
				Shortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "Veyron.lnk"), exe);
			}
			catch (Exception) { /* no shortcut is no reason to stop */ }
		}

		if (test)
			return;
		ProcessStartInfo start = new ProcessStartInfo(exe);
		start.WorkingDirectory = Path.GetDirectoryName(exe);
		Process.Start(start);
	}

	static void Install(string root, string launcherDir, string versionFile, string version)
	{
		Directory.CreateDirectory(root);
		if (Directory.Exists(launcherDir))
			Directory.Delete(launcherDir, true);
		Directory.CreateDirectory(launcherDir);
		using (Stream zip = Assembly.GetExecutingAssembly().GetManifestResourceStream("launcher.zip"))
		using (ZipArchive archive = new ZipArchive(zip, ZipArchiveMode.Read))
		{
			foreach (ZipArchiveEntry entry in archive.Entries)
			{
				string path = Path.GetFullPath(Path.Combine(launcherDir, entry.FullName));
				if (!path.StartsWith(Path.GetFullPath(launcherDir), StringComparison.OrdinalIgnoreCase))
					continue;
				if (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\"))
				{
					Directory.CreateDirectory(path);
					continue;
				}
				Directory.CreateDirectory(Path.GetDirectoryName(path));
				using (Stream input = entry.Open())
				using (FileStream output = File.Create(path))
					input.CopyTo(output);
			}
		}
		File.WriteAllText(versionFile, version);
	}

	static string ReadResource(string name)
	{
		using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
		using (StreamReader r = new StreamReader(s))
			return r.ReadToEnd();
	}

	static void Shortcut(string lnk, string target)
	{
		Type shellType = Type.GetTypeFromProgID("WScript.Shell");
		object shell = Activator.CreateInstance(shellType);
		object link = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { lnk });
		Type linkType = link.GetType();
		linkType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { target });
		linkType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, link, new object[] { Path.GetDirectoryName(target) });
		linkType.InvokeMember("Description", BindingFlags.SetProperty, null, link, new object[] { "Veyron RSPS" });
		linkType.InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
	}

	static Form ProgressForm()
	{
		Form f = new Form();
		f.Text = "Veyron";
		f.FormBorderStyle = FormBorderStyle.FixedDialog;
		f.StartPosition = FormStartPosition.CenterScreen;
		f.ClientSize = new Size(360, 110);
		f.MaximizeBox = false;
		f.MinimizeBox = false;
		f.BackColor = Color.FromArgb(18, 8, 7);
		try { f.Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location); } catch (Exception) { }
		Label label = new Label();
		label.Text = "Installing Veyron...";
		label.ForeColor = Color.FromArgb(233, 223, 214);
		label.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
		label.AutoSize = false;
		label.TextAlign = ContentAlignment.MiddleCenter;
		label.SetBounds(10, 18, 340, 30);
		ProgressBar bar = new ProgressBar();
		bar.Style = ProgressBarStyle.Marquee;
		bar.SetBounds(30, 62, 300, 18);
		f.Controls.Add(label);
		f.Controls.Add(bar);
		return f;
	}
}
