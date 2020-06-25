import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import soot.G;
import soot.PackManager;
import soot.Scene;
import soot.Transform;

public class Main {
	private static List<SourceInfo> sources = new ArrayList<>();
	protected static final MyLogger LOGGER = new MyLogger(true, false);
	private static boolean copy = false;
	private static ChoiceList choices = new ChoiceList();
	private static String outputDir = "";

	public static void main(String[] args) throws IOException, ParseException {
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message");
		options.addOption("apk", "apkPath", true, "The path to apk file");
		options.addOption("f", "findings", true, "The path to the findings of the apk file in TAF format");
		options.addOption("c", "choices", true, "The path to chosen statements from last run in *_delta_choices.json.");
		options.addOption("dir", "directory", true,
				"The path to the directory contains both the apk file and the findings in TAF format");
		options.addOption("p", "androidPlatform", true, "The path to android platform jars");
		options.addOption("o", "outputDir", true,
				"(Optional)The path to output directory, default is workingdirectory/delta_apks");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		HelpFormatter helper = new HelpFormatter();
		String cmdLineSyntax = "Process an apk \n Either -apk <apk> -f <findings>  -p <android platform jars> \n "
				+ "Or -dir <directory of apk and findings> -p <android platform jars>\n\n";
		if (cmd.hasOption('h')) {
			helper.printHelp(cmdLineSyntax, options);
			return;
		}

		if (!cmd.hasOption("dir") && (!cmd.hasOption("apk") || !cmd.hasOption("p") || !cmd.hasOption("f"))) {
			helper.printHelp(cmdLineSyntax, options);
			return;
		} else {
			if (cmd.hasOption("dir") && !cmd.hasOption("p")) {
				helper.printHelp(cmdLineSyntax, options);
				return;
			}
		}

		String androidJarPath = null;
		if (cmd.hasOption("p")) {
			androidJarPath = cmd.getOptionValue("p");
		}
		String apkPath = null;
		String findingsPath = null;
		String appDir = null;

		if (cmd.hasOption("dir")) {
			appDir = cmd.getOptionValue("dir");
			File appDirfile = new File(appDir);
			String apkFileName = appDirfile.getName() + ".apk";
			String findingsFileName = appDirfile.getName() + "_findings.json";
			apkPath = appDir + File.separator + apkFileName;
			findingsPath = appDir + File.separator + findingsFileName;
		}

		if (cmd.hasOption("apk") && cmd.hasOption("f")) {
			apkPath = cmd.getOptionValue("apk");
			findingsPath = cmd.getOptionValue("f");
		}

		String outputDirName = "delta_apks";
		if (cmd.hasOption("o")) {
			outputDirName = cmd.getOptionValue("o");
		}
		String choicesPath = null;
		if (cmd.hasOption("c")) {
			choicesPath = cmd.getOptionValue("c");
		}
		outputDir = outputDirName;
		readFindings(findingsPath);
		boolean hasChoices = false;
		if (choicesPath != null)
			hasChoices = readChoices(choicesPath);
		File output = new File(outputDirName);
		if (output.exists())
			org.apache.commons.io.FileUtils.deleteDirectory(output);
		// the following code creates delta apk for each taint flow
		String apkName = "";
		for (SourceInfo source : sources) {
			String outputDir = outputDirName + File.separator + "delta_" + source.ID;
			Choice choice = null;
			if (hasChoices)
				choice = choices.getChoice(source.ID);
			outputDeltaAPK(apkPath, androidJarPath, source, outputDir, choice);
			File outputFile = new File(outputDir);
			for (File f : outputFile.listFiles()) {
				if (f.getName().endsWith(".apk")) {
					apkName = f.getName().split(".apk")[0];
					String newName = outputDir + File.separator + apkName + "_delta_" + source.ID + ".apk";
					File newFile = new File(newName);
					f.renameTo(newFile);
				}
			}
		}
		if (!hasChoices)
			writeChoices(apkName);
		// the following code copies delta apks to benchmark directory
		final String targetAppDir = appDir;
		final String taregetOutputDirName = outputDirName;
		if (copy) {
			Files.walk(Paths.get(outputDirName)).filter(Files::isRegularFile).forEach(f -> {
				if (f.toString().endsWith(".apk")) {
					File targetFile = new File(targetAppDir + File.separator + taregetOutputDirName + File.separator
							+ f.getFileName().toString());
					targetFile.getParentFile().mkdirs();
					try {
						com.google.common.io.Files.copy(f.toFile(), targetFile);
						System.out.println("Copied delta apk to " + targetFile);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			org.apache.commons.io.FileUtils.deleteDirectory(output);
		}
	}

	private static void outputDeltaAPK(String apk, String androidJarPath, SourceInfo sourceInfo, String outputDir,
			Choice choice) {
		G.reset();
		soot.options.Options.v().set_include_all(true);
		soot.options.Options.v().set_no_bodies_for_excluded(true);
		soot.options.Options.v().set_allow_phantom_refs(true);

		soot.options.Options.v().set_process_dir(Collections.singletonList(apk));
		soot.options.Options.v().set_android_jars(androidJarPath);

		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		soot.options.Options.v().set_keep_line_number(true);
		soot.options.Options.v().set_process_multiple_dex(true);
		soot.options.Options.v().setPhaseOption("jb", "use-original-names:true");

		soot.options.Options.v().set_soot_classpath(Scene.v().getAndroidJarPath(androidJarPath, apk));
		soot.options.Options.v().set_force_overwrite(true);
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_output_dir(outputDir);
		Scene.v().loadNecessaryClasses();
		LOGGER.info("****************************************************************************");
		LOGGER.info("\nStarting search matches for source " + sourceInfo.ID);
		LOGGER.info("\nSource: " + sourceInfo.toString());
		DeltaBodyTransformer transformer = new DeltaBodyTransformer(sourceInfo, choice);
		PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", transformer));
		PackManager.v().runBodyPacks();
		PackManager.v().writeOutput();
		choices.add(transformer.getChoice());
	}

	private static boolean readChoices(String choicesPath)
			throws JsonSyntaxException, JsonIOException, FileNotFoundException {
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		choices = gson.fromJson(new InputStreamReader(new FileInputStream(new File(choicesPath))), ChoiceList.class);
		return !choices.isEmpty();
	}

	private static void writeChoices(String apkName) {
		if (choices.cleanUp() > 0) {
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			String output = gson.toJson(choices);
			String outputPath = outputDir + File.separator + apkName + "_delta_choices.json";
			try {
				FileWriter file = new FileWriter(new File(outputPath), false);
				PrintWriter printWriter = new PrintWriter(file);
				printWriter.println(output);
				printWriter.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * This method read source from each finding.
	 *
	 * @param findingsPath
	 */
	private static void readFindings(String findingsPath) {
		try {
			JsonParser parser = new JsonParser();
			JsonObject obj = parser.parse(new FileReader(findingsPath)).getAsJsonObject();
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			JsonArray findings = obj.getAsJsonArray("findings");
			for (int i = 0; i < findings.size(); i++) {
				JsonObject finding = findings.get(i).getAsJsonObject();
				int ID = -1;
				if (finding.has("ID")) {
					ID = finding.get("ID").getAsInt();
				}
				if (finding.has("source")) {
					JsonObject source = finding.get("source").getAsJsonObject();
					String statement = null;
					String methodName = null;
					String className = null;
					int lineNo = -1;
					String targetName = null;
					int targetNo = -1;
					if (source.has("statement")) {
						statement = source.getAsJsonPrimitive("statement").getAsString();
					}
					if (source.has("methodName")) {
						methodName = source.getAsJsonPrimitive("methodName").getAsString();
					}
					if (source.has("className")) {
						className = source.getAsJsonPrimitive("className").getAsString();
					}
					if (source.has("lineNo")) {
						lineNo = source.getAsJsonPrimitive("lineNo").getAsInt();
					}
					if (source.has("targetName")) {
						targetName = source.getAsJsonPrimitive("targetName").getAsString();
					}
					if (source.has("targetNo")) {
						targetNo = source.getAsJsonPrimitive("targetNo").getAsInt();
					}
					if (statement != null && methodName != null && className != null && targetName != null
							&& ID != -1) {
						SourceInfo s = new SourceInfo(statement, methodName, className, lineNo, targetName, targetNo,
								ID);
						if (!sources.contains(s)) {
							sources.add(s);
						}
					}
				}
			}
		} catch (JsonIOException | JsonSyntaxException | FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
