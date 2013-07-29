/*
 * eID Applet Project.
 * Copyright (C) 2008-2010 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package be.fedict.eid.applet.maven;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.map.HashedMap;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import be.fedict.eid.applet.shared.AppletProtocolMessageCatalog;
import be.fedict.eid.applet.shared.annotation.Description;
import be.fedict.eid.applet.shared.annotation.HttpBody;
import be.fedict.eid.applet.shared.annotation.HttpHeader;
import be.fedict.eid.applet.shared.annotation.NotNull;
import be.fedict.eid.applet.shared.annotation.ProtocolStateAllowed;
import be.fedict.eid.applet.shared.annotation.ResponsesAllowed;
import be.fedict.eid.applet.shared.annotation.StartRequestMessage;
import be.fedict.eid.applet.shared.annotation.StateTransition;
import be.fedict.eid.applet.shared.annotation.StopResponseMessage;
import be.fedict.eid.applet.shared.protocol.ProtocolState;
import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;

/**
 * eID Applet Docbook Plugin.
 * 
 * @author fcorneli
 * @goal generate-docbook
 */
public class DocbookMojo extends AbstractMojo {

	/**
	 * The Protocol Message Catalog Class.
	 * 
	 * @parameter
	 * @required
	 */
	private String protocolMessageCatalogClass;

	/**
	 * Directory containing the generated docbook XML.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * Name of the generated docbook XML.
	 * 
	 * @parameter
	 * @required
	 */
	private String docbookFile;

	/**
	 * Name of the generated graph PNG.
	 * 
	 * @parameter
	 * @required
	 */
	private String graphFile;

	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("executing...");
		getLog().info(
				"Protocol Message Catalog Class: "
						+ this.protocolMessageCatalogClass);

		File outputFile = new File(this.outputDirectory, this.docbookFile);
		getLog().info("Output docbook file: " + outputFile.getAbsolutePath());

		File graphFile = new File(this.outputDirectory, this.graphFile);
		getLog().info("Output graph file: " + graphFile.getAbsolutePath());

		this.outputDirectory.mkdirs();
		try {
			generateDocbook(outputFile);
		} catch (Exception e) {
			getLog().error("Error generating docbook: " + e.getMessage(), e);
			throw new MojoExecutionException("Error generating docbook: "
					+ e.getMessage(), e);
		}

		try {
			generateGraph(graphFile);
		} catch (IOException e) {
			getLog().error("Error generating graph: " + e.getMessage(), e);
			throw new MojoExecutionException("Error generating graph: "
					+ e.getMessage(), e);
		}
	}

	private void generateDocbook(File docbookFile)
			throws FileNotFoundException, IllegalAccessException {
		PrintWriter writer = new PrintWriter(docbookFile);
		writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		writer.println("<section version=\"5.0\" ");
		writer.println("xsi:schemaLocation=\"http://docbook.org/ns/docbook http://www.docbook.org/xml/5.0/xsd/docbook.xsd\"");
		writer
				.println("xmlns=\"http://docbook.org/ns/docbook\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
		writer.println("<title>eID Applet Protocol Messages</title>");
		writer
				.println("<para>The following documentation has been generated automatically.</para>");
		writer.println("<!-- Autogenerated by eid-applet-docbook-plugin -->");

		AppletProtocolMessageCatalog catalog = new AppletProtocolMessageCatalog();
		List<Class<?>> catalogClasses = catalog.getCatalogClasses();
		for (Class<?> catalogClass : catalogClasses) {
			ResponsesAllowed responsesAllowedAnnotation = catalogClass
					.getAnnotation(ResponsesAllowed.class);
			if (null == responsesAllowedAnnotation) {
				/*
				 * We describe request messages first.
				 */
				continue;
			}
			describeClass(catalogClass, writer);
		}

		for (Class<?> catalogClass : catalogClasses) {
			ResponsesAllowed responsesAllowedAnnotation = catalogClass
					.getAnnotation(ResponsesAllowed.class);
			if (null != responsesAllowedAnnotation) {
				/*
				 * We describe request messages first.
				 */
				continue;
			}
			describeClass(catalogClass, writer);
		}

		writer.println("</section>");
		writer.close();
	}

	private void describeClass(Class<?> catalogClass, PrintWriter writer)
			throws IllegalArgumentException, IllegalAccessException {
		writer.println("<section id=\"" + catalogClass.getSimpleName() + "\">");
		writer.println("<title>" + catalogClass.getSimpleName() + "</title>");

		StartRequestMessage startRequestMessage = catalogClass
				.getAnnotation(StartRequestMessage.class);
		if (null != startRequestMessage) {
			writer.println("<para>");
			writer
					.println("This message starts a communication session between eID Applet and eID Applet Service.");
			writer.println("It sets the protocol state to: "
					+ startRequestMessage.value());
			writer.println("</para>");
		}

		StopResponseMessage stopResponseMessage = catalogClass
				.getAnnotation(StopResponseMessage.class);
		if (null != stopResponseMessage) {
			writer.println("<para>");
			writer
					.println("This message stops a communication session between eID Applet and the eID Applet Service.");
			writer.println("</para>");
		}

		ProtocolStateAllowed protocolStateAllowed = catalogClass
				.getAnnotation(ProtocolStateAllowed.class);
		if (null != protocolStateAllowed) {
			writer.println("<para>");
			writer
					.println("This message is only accepted if the eID Applet Service protocol state is: "
							+ protocolStateAllowed.value());
			writer.println("</para>");
		}

		Field bodyField = null;

		writer.println("<table>");
		writer.println("<title>" + catalogClass.getSimpleName()
				+ " HTTP headers</title>");
		writer.println("<tgroup cols=\"3\">");
		{
			writer.println("<colspec colwidth=\"2*\" />");
			writer.println("<colspec colwidth=\"1*\" />");
			writer.println("<colspec colwidth=\"2*\" />");
			writer.println("<thead>");
			writer.println("<row>");
			writer.println("<entry>Header name</entry>");
			writer.println("<entry>Required</entry>");
			writer.println("<entry>Value</entry>");
			writer.println("</row>");
			writer.println("</thead>");
			writer.println("<tbody>");
			{
				Field[] fields = catalogClass.getFields();
				for (Field field : fields) {
					if (field.getAnnotation(HttpBody.class) != null) {
						bodyField = field;
					}
					HttpHeader httpHeaderAnnotation = field
							.getAnnotation(HttpHeader.class);
					if (null == httpHeaderAnnotation) {
						continue;
					}
					writer.println("<row>");
					writer.println("<entry>");
					writer.println("<code>" + httpHeaderAnnotation.value()
							+ "</code>");
					writer.println("</entry>");
					writer.println("<entry>");
					writer.println((null != field.getAnnotation(NotNull.class))
							|| (0 != (field.getModifiers() & Modifier.FINAL)));
					writer.println("</entry>");
					writer.println("<entry>");
					if (0 != (field.getModifiers() & Modifier.FINAL)) {
						Object value = field.get(null);
						writer.println("<code>" + value.toString() + "</code>");
					} else {
						writer.println("Some "
								+ field.getType().getSimpleName() + " value.");
					}
					writer.println("</entry>");
					writer.println("</row>");
				}
			}
			writer.println("</tbody>");
		}
		writer.println("</tgroup>");
		writer.println("</table>");

		if (null != bodyField) {
			writer.println("<para>HTTP body should contain the data.</para>");
			Description description = bodyField
					.getAnnotation(Description.class);
			if (null != description) {
				writer.println("<para>Body content: ");
				writer.println(description.value());
				writer.println("</para>");
			}
		}

		ResponsesAllowed responsesAllowedAnnotation = catalogClass
				.getAnnotation(ResponsesAllowed.class);
		if (null != responsesAllowedAnnotation) {
			Class<?>[] responsesAllowed = responsesAllowedAnnotation.value();
			writer.println("<para>");
			writer
					.println("Allowed eID Applet Service response messages are: ");
			for (Class<?> responseAllowed : responsesAllowed) {
				writer.println("<xref linkend=\""
						+ responseAllowed.getSimpleName() + "\"/>");
			}
			writer.println("</para>");
		}

		StateTransition stateTransition = catalogClass
				.getAnnotation(StateTransition.class);
		if (null != stateTransition) {
			writer.println("<para>");
			writer
					.println("This message will perform an eID Applet protocol state transition to: "
							+ stateTransition.value());
			writer.println("</para>");
		}

		writer.println("</section>");
	}

	public static void generateGraph(File graphFile) throws IOException {
		BasicVisualizationServer<String, String> visualization = createGraph();
		graphToFile(visualization, graphFile);
	}

	private static void graphToFile(
			BasicVisualizationServer<String, String> visualization, File file)
			throws IOException {
		Dimension size = visualization.getSize();
		int width = (int) (size.getWidth() + 1);
		int height = (int) (size.getHeight() + 1);
		BufferedImage bufferedImage = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = bufferedImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, 900, 650);
		visualization.setBounds(0, 0, 900, 650);
		visualization.paint(graphics);
		graphics.dispose();
		ImageIO.write(bufferedImage, "png", file);
	}

	private static BasicVisualizationServer<String, String> createGraph() {
		AppletProtocolMessageCatalog catalog = new AppletProtocolMessageCatalog();
		List<Class<?>> catalogClasses = catalog.getCatalogClasses();

		Map<ProtocolState, List<String>> allowedProtocolStates = new HashedMap<ProtocolState, List<String>>();
		String startMessage = null;
		List<String> stopMessages = new LinkedList<String>();

		Graph<String, String> graph = new SparseMultigraph<String, String>();
		for (Class<?> messageClass : catalogClasses) {
			StartRequestMessage startRequestMessageAnnotation = messageClass
					.getAnnotation(StartRequestMessage.class);
			if (null != startRequestMessageAnnotation) {
				if (null != startMessage) {
					throw new RuntimeException(
							"only one single entry point possible");
				}
				startMessage = messageClass.getSimpleName();
			}
			StopResponseMessage stopResponseMessageAnnotation = messageClass
					.getAnnotation(StopResponseMessage.class);
			if (null != stopResponseMessageAnnotation) {
				stopMessages.add(messageClass.getSimpleName());
			}
			graph.addVertex(messageClass.getSimpleName());
			ProtocolStateAllowed protocolStateAllowedAnnotation = messageClass
					.getAnnotation(ProtocolStateAllowed.class);
			if (null != protocolStateAllowedAnnotation) {
				ProtocolState protocolState = protocolStateAllowedAnnotation
						.value();
				List<String> messages = allowedProtocolStates
						.get(protocolState);
				if (null == messages) {
					messages = new LinkedList<String>();
					allowedProtocolStates.put(protocolState, messages);
				}
				messages.add(messageClass.getSimpleName());
			}
		}

		int edgeIdx = 0;
		for (Class<?> messageClass : catalogClasses) {
			ResponsesAllowed responsesAllowedAnnotation = messageClass
					.getAnnotation(ResponsesAllowed.class);
			if (null != responsesAllowedAnnotation) {
				Class<?>[] responseClasses = responsesAllowedAnnotation.value();
				for (Class<?> responseClass : responseClasses) {
					String edgeName = "edge-" + edgeIdx;
					graph.addEdge(edgeName, messageClass.getSimpleName(),
							responseClass.getSimpleName(), EdgeType.DIRECTED);
					edgeIdx++;
				}
			}
			StateTransition stateTransitionAnnotation = messageClass
					.getAnnotation(StateTransition.class);
			if (null != stateTransitionAnnotation) {
				ProtocolState protocolState = stateTransitionAnnotation.value();
				List<String> messages = allowedProtocolStates
						.get(protocolState);
				for (String message : messages) {
					graph.addEdge("edge-" + edgeIdx, messageClass
							.getSimpleName(), message, EdgeType.DIRECTED);
					edgeIdx++;
				}
			}
		}

		Layout<String, String> layout = new CircleLayout<String, String>(graph);
		layout.setSize(new Dimension(900, 600));

		BasicVisualizationServer<String, String> visualization = new BasicVisualizationServer<String, String>(
				layout);
		visualization.getRenderContext().setVertexLabelTransformer(
				new ToStringLabeller<String>());
		Transformer<String, Paint> myVertexTransformer = new MyVertexTransformer(
				startMessage, stopMessages);
		visualization.getRenderContext().setVertexFillPaintTransformer(
				myVertexTransformer);
		Transformer<String, Paint> myEdgeTransformer = new MyEdgeTransformer();
		visualization.getRenderContext().setEdgeDrawPaintTransformer(
				myEdgeTransformer);
		visualization.getRenderer().getVertexLabelRenderer().setPosition(
				Position.AUTO);
		visualization.setPreferredSize(new Dimension(900, 650));
		visualization.setBackground(Color.WHITE);
		return visualization;
	}
}
