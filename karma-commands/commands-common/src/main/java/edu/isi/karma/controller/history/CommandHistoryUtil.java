package edu.isi.karma.controller.history;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandFactory;
import edu.isi.karma.controller.history.CommandHistory.HistoryArguments;
import edu.isi.karma.controller.update.HistoryUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.controller.update.WorksheetUpdateFactory;
import edu.isi.karma.modeling.alignment.Alignment;
import edu.isi.karma.modeling.alignment.AlignmentManager;
import edu.isi.karma.modeling.semantictypes.SemanticTypeUtil;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.HNode.HNodeType;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.webserver.ExecutionController;
import edu.isi.karma.webserver.WorkspaceRegistry;

public class CommandHistoryUtil {
	private final static Set<CommandConsolidator> consolidators = new HashSet<CommandConsolidator>();
	private final List<Command> commands = new ArrayList<Command>();
	private Workspace workspace;
	private String worksheetId;
	HashMap<String, CommandFactory> commandFactoryMap;
	static Logger logger = Logger.getLogger(CommandHistoryUtil.class);
	static {
		Reflections reflections = new Reflections("edu.isi.karma");
		Set<Class<? extends CommandConsolidator>> subTypes =
				reflections.getSubTypesOf(CommandConsolidator.class);
		for (Class<? extends CommandConsolidator> subType : subTypes)
		{
			if(!Modifier.isAbstract(subType.getModifiers()) && !subType.isInterface()) {
				try {
					consolidators.add(subType.newInstance());
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	public CommandHistoryUtil(JSONArray historyJSON, Workspace workspace, String worksheetId) {
		this.worksheetId = worksheetId;
		this.workspace = workspace;
		ExecutionController ctrl = WorkspaceRegistry.getInstance().getExecutionController(workspace.getId());
		commandFactoryMap = ctrl.getCommandFactoryMap();
		commands.addAll(getCommandsFromHistoryJSON(historyJSON, false));
	}

	public CommandHistoryUtil(List<Command> commands, Workspace workspace, String worksheetId) {
		this.worksheetId = worksheetId;
		this.workspace = workspace;
		ExecutionController ctrl = WorkspaceRegistry.getInstance().getExecutionController(workspace.getId());
		commandFactoryMap = ctrl.getCommandFactoryMap();
		this.commands.addAll(commands);
	}
	private Map<Command, List<Command> > generateGraph() {

		Map<Command, List<Command> > dag = new HashMap<Command, List<Command>>();
		Map<String, List<Command> > outputMapping = new HashMap<String, List<Command> >();
		for (Command command : commands) {
			Set<String> outputs = command.getOutputColumns();
			for (String output : outputs) {
				List<Command> tmp = outputMapping.get(output);
				if (tmp == null)
					tmp = new ArrayList<Command>();
				tmp.add(command);
				outputMapping.put(output, tmp);
			}
		}
		for (Command command : commands) {
			Set<String> inputs = command.getInputColumns();
			for (String input : inputs) {
				List<Command> outputCommands = outputMapping.get(input);
				if (outputCommands != null) {
					List<Command> edges = dag.get(command);
					if (edges == null)
						edges = new ArrayList<Command>();
					for (Command tmp : outputCommands) {
						if (tmp != command)
							edges.add(tmp);
					}
					dag.put(command, edges);
				}
			}					
		}
		
		//		for (Entry<Command, List<Command>> entry : dag.entrySet()) {
		//			Command key = entry.getKey();
		//			List<Command> value = entry.getValue();
		//			System.out.print(key.getCommandName() + "inputs: " + key.getInputColumns() + "outputs: " + key.getOutputColumns());
		//			System.out.print("=");
		//			for (Command command : value)
		//				System.out.print(command.getCommandName() + "inputs: " + command.getInputColumns() + "outputs: " + command.getOutputColumns());
		//			System.out.println();
		//		}
		return dag;
	}
	
	public Set<String> generateInputColumns() {
		Map<Command, List<Command> > dag = generateGraph(); 
		Set<String> inputColumns = new HashSet<String>();
		for (Command t : commands) {
			if (t.getCommandName().equals("SetSemanticTypeCommand") || t.getCommandName().equals("SetMetaPropertyCommand")) {
				inputColumns.addAll(getParents(t, dag));
			}
		}
		return inputColumns;
	}
	
	public Set<String> generateOutputColumns() {
		Set<String> outputColumns = new HashSet<String>();
		for (Command t : commands) {
			if (t.getOutputColumns() != null)
				outputColumns.addAll(t.getOutputColumns());
		}
		return outputColumns;
	}


	private Set<String> getParents(Command c, Map<Command, List<Command> >dag) {
		List<Command> parents = dag.get(c);
		Set<String> terminalColumns = new HashSet<String>();
		if (parents == null || parents.size() == 0)
			terminalColumns.addAll(c.getInputColumns());
		else {
			for (Command t : parents) {
				terminalColumns.addAll(getParents(t, dag));
				for (String hNodeId : c.getInputColumns()) {
					HNode hn = workspace.getFactory().getHNode(hNodeId);
					if (hn.getHNodeType() == HNodeType.Regular)
						terminalColumns.add(hNodeId);
				}
			}
		}
		return terminalColumns;
	}

	public List<Command> consolidateHistory() throws CommandException {
		List<Command> refinedCommands = new ArrayList<Command>(commands);
		for (CommandConsolidator con : consolidators) {
			refinedCommands = con.consolidateCommand(refinedCommands, workspace);
		}
		if (!checkDependency(refinedCommands))
			return commands;
//		JSONArray refinedhistory = new JSONArray();
//		for (Command refined : refinedCommands)
//			refinedhistory.put(workspace.getCommandHistory().getCommandJSON(workspace, refined));
		this.commands.clear();
		this.commands.addAll(refinedCommands);
		return commands;

	}

	private boolean checkDependency(List<Command> commands) {
		Set<String> OutputhNodeIds = new HashSet<String>();
		for (HNode hnode : workspace.getFactory().getAllHNodes()) {
			if (hnode.getHNodeType() == HNodeType.Regular)
				OutputhNodeIds.add(hnode.getId());
		}
		boolean dependency = true;
		for (Command command : commands) {
			if (command.getInputColumns().size() > 0) {
				for (String hNodeId : command.getInputColumns()) {
					if (!OutputhNodeIds.contains(hNodeId))
						dependency = false;
				}
			}
			if (command.getOutputColumns().size() > 0) {
				OutputhNodeIds.addAll(command.getOutputColumns());
			}
		}
		return dependency;
	}
	
	public UpdateContainer replayHistory() {
		CommandHistory commandHistory = workspace.getCommandHistory();
		commandHistory.removeCommands(workspace, worksheetId);
		JSONArray redoCommandsArray = new JSONArray();
		for (Command refined : commands)
			redoCommandsArray.put(workspace.getCommandHistory().getCommandJSON(workspace, refined));
		commands.clear();
		commands.addAll(getCommandsFromHistoryJSON(redoCommandsArray, true));
		UpdateContainer uc = new UpdateContainer(new HistoryUpdate(commandHistory));
		uc.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(worksheetId));
		Alignment alignment = AlignmentManager.Instance().getAlignmentOrCreateIt(workspace.getId(), worksheetId, workspace.getOntologyManager());
		// Compute the semantic type suggestions
		SemanticTypeUtil.computeSemanticTypesSuggestion(workspace.getWorksheet(worksheetId), workspace
				.getCrfModelHandler(), workspace.getOntologyManager());
		uc.append(WorksheetUpdateFactory.createSemanticTypesAndSVGAlignmentUpdates(worksheetId, workspace, alignment));		
		return uc;
	}
	
	public List<Command> getCommands() {
		return new ArrayList<Command>(commands);
	}
	
	private List<Command> getCommandsFromHistoryJSON(JSONArray historyJSON, boolean replayHistory) {
		List<Command> commands = new ArrayList<Command>();
		for (int i = 0; i < historyJSON.length(); i++) {
			JSONObject commObject = historyJSON.getJSONObject(i);
			JSONArray inputParamArr = (JSONArray) commObject.get(HistoryArguments.inputParameters.name());
			String commandName = (String)commObject.get(HistoryArguments.commandName.name());
			WorksheetCommandHistoryExecutor ex = new WorksheetCommandHistoryExecutor(worksheetId, workspace);
			ex.normalizeCommandHistoryJsonInput(workspace, worksheetId, inputParamArr, commandName);
			System.out.println(inputParamArr.toString(4));
			CommandFactory cf = commandFactoryMap.get(commObject.get(HistoryArguments.commandName.name()));
			if(cf != null) {
				try { // This is sort of a hack the way I did this, but could not think of a better way to get rid of the dependency
					Command comm = cf.createCommand(inputParamArr, workspace);
					if(comm != null){
						commands.add(comm);
						workspace.getCommandHistory().doCommand(comm, workspace, true);
					}

				} catch (Exception ignored) {

				}
			}
		}
		return commands;
	}
}
