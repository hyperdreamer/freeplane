package org.freeplane.api;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.swing.Icon;

import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestOptions;


/** Access to global state: in scripts, this is available as global variable <code>c</code> - read-write. */
public interface Controller extends ControllerRO, HeadlessMapCreator {
	void centerOnNode(Node center);

	/** Starts editing node, normally in the inline editor. Does not block until edit has finished.
	 * @since 1.2.2 */
	void edit(Node node);

	/** opens the appropriate popup text editor. Does not block until edit has finished.
	 * @since 1.2.2 */
	void editInPopup(Node node);

	void select(Node toSelect);

	/** selects multiple Nodes.
	 * @since 1.4 */
	void select(Collection<? extends Node> toSelect);

	/** selects branchRoot and all children */
	void selectBranch(Node branchRoot);

	/** same as {@link #select(Collection)} */
	void selectMultipleNodes(Collection<? extends Node> toSelect);

	/** reset undo / redo lists and deactivate Undo for current script */
	void deactivateUndo();

	/** invokes undo once - for testing purposes mainly.
	 * @since 1.2 */
	void undo();

	/** invokes redo once - for testing purposes mainly.
	 * @since 1.2 */
	void redo();

	/** The main info for the status line with key="standard", use null to remove. Removes icon if there is one. */
	void setStatusInfo(String info);

	/** Info for status line, null to remove. Removes icon if there is one.
	 * @see #setStatusInfo(String, String, String) */
	void setStatusInfo(String infoPanelKey, String info);

	/** Info for status line - text and icon - null stands for "remove" (text or icon)
	 * @param infoPanelKey "standard" is the left most standard info panel. If a panel with
	 *        this name doesn't exist it will be created.
	 * @param info Info text
	 * @param iconKey key as those that are used for nodes (see {@link Icons#addIcon(String)}).
	 * <pre>
	 *   println("all available icon keys: " + FreeplaneIconUtils.listStandardIconKeys())
	 *   c.setStatusInfo("standard", "hi there!", "button_ok");
	 * </pre>
	 * @see org.freeplane.core.ui.svgicons.FreeplaneIconFactory
	 * @since 1.2 */
	void setStatusInfo(String infoPanelKey, String info, String iconKey);

	/** @deprecated since 1.2 - use {@link #setStatusInfo(String, String, String)} */
	@Deprecated
	void setStatusInfo(String infoPanelKey, Icon icon);

	/** @deprecated since 1.7.5 - use {@link #mapLoader(File)} */
	@Override
	@Deprecated
	Loader load(File file);

	/** @deprecated since 1.7.5 - use {@link #mapLoader(URL)} */
	@Override
	@Deprecated
	Loader load(URL url);

	/** @deprecated since 1.7.5 - use {@link #mapLoader(String)} */
	@Override
	@Deprecated
	Loader load(String input);

	/**
	 * Returns {@link Loader} for accessing or loading mind map from file.
	 *
	 * @since 1.7.5
	 */
	@Override
	Loader mapLoader(File file);

	/**
	 * Returns {@link Loader} for accessing or loading mind map from URL.
	 *
	 * @since 1.7.5
	 */
	@Override
	Loader mapLoader(URL file);

	/**
	 * Returns {@link Loader} for accessing or loading mind map from file.
	 *
	 * @since 1.7.5
	 */
	@Override
	Loader mapLoader(String file);

	/**
	 * opens a new map with a default name in the foreground.
	 * @since 1.2
	 *
	 * @deprecated since 1.7.10 - use {@link #newMindMap()}
	 */
	default Map newMap() {return (Map) newMindMap();}

	/**
	 * @deprecated since 1.6.16 - use {@link #mapLoader(URL)}
	 * @since 1.2 */
	@Deprecated
	default Map newMap(URL url) {
		return (Map) mapLoader(url).withView().getMindMap();
	}

	/**
	 * @since 1.5
	 */
	default Map newMapFromTemplate(File templateFile) {
		return (Map) mapLoader(templateFile).withView().unsetMapLocation().getMindMap();
	}

	/**
	 * opens a new map with a default name in the foreground.
	 * @since 1.7.10 */
	MindMap newMindMap();

	/** a value of 1 means 100%.
	 * @since 1.2 */
	void setZoom(final float ratio);

	/**
	 * a list of all opened maps.
	 *
	 * @since 1.5
	 *
	 * @deprecated since 1.7.10 - use {@link #getOpenMindMaps()}
	 *
	 * */
	@Deprecated
	@SuppressWarnings("unchecked")
	default List<? extends Map> getOpenMaps() {return (List<? extends Map>) getOpenMindMaps();}

	/**
	 * a list of all opened maps.
	 * @since 1.5
	 * */
	List<? extends MindMap> getOpenMindMaps();

	/**
	 * @since 1.7.10
	 */
	ExecutorService getMainThreadExecutorService();

	/**
	 * @since 1.13.3
	 * Moves a group of nodes together. <br>
	 * Useful, e.g., when moving a summary-node group (FirstGroupNode .. SummaryNode).
	 * @param nodes the nodes to move
	 * @param parent the new parent node
	 * @param position the new position within the parent
	 */
	void moveNodes(List<Node> nodes, Node parent, int position);

	/**
	 * Starts an asynchronous AI request for raw prompt text and delivers
	 * the terminal result through the callback.
	 *
	 * <p>The supplied options must include a positive timeout and a non-null
	 * {@link org.freeplane.api.ai.AiRequestMode}. See
	 * {@link AiRequestOptions.Builder#systemMessage(String)} and
	 * {@link AiRequestOptions.Builder#exactSystemMessage(String)} for the
	 * difference between a base system-message override and an exact system
	 * instruction.</p>
	 *
	 * <p>May throw {@link org.freeplane.api.ai.AiRequestRejectedException}
	 * for same-thread pre-acceptance rejection. In Groovy scripts,
	 * implementations may also support the natural trailing-closure form, for
	 * example
	 * <code>c.askAi("Prompt", AiRequestOptions.builder().timeout(Duration.ofSeconds(30)).mode(org.freeplane.api.ai.AiRequestMode.HIDDEN).build()) { result -&gt; println(result.status) }</code>.</p>
	 * @since 1.13.3
	 */
	AiRequestHandle askAi(String prompt, AiRequestOptions options, AiRequestCallback callback);

	/**
	 * Starts an asynchronous AI request from a saved AI prompt name using
	 * the supplied timeout and the saved prompt's stored execution
	 * defaults.
	 * @since 1.13.3
	 */
	AiRequestHandle runAiPrompt(String promptName, Duration timeout, AiRequestCallback callback);

	/**
	 * Starts an asynchronous AI request from a saved AI prompt name using
	 * the supplied options to override saved-prompt execution defaults.
	 *
	 * <p>Unset option fields inherit from the saved prompt where applicable.
	 * Explicit values in {@link AiRequestOptions} override saved-prompt values;
	 * unset fields inside {@link org.freeplane.api.ai.AiModelConfiguration}
	 * inherit independently.</p>
	 * @since 1.13.3
	 */
	AiRequestHandle runAiPrompt(String promptName, AiRequestOptions options, AiRequestCallback callback);
}
