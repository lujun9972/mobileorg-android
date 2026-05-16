package com.matburt.mobileorg.util;

import android.content.ContentResolver;
import android.content.Context;

import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.OrgData.OrgNode;

/**
 * OrgRenderer - A line-level state machine that converts org-mode text to HTML.
 *
 * This renderer processes org-mode content through a state machine that handles:
 * - Paragraphs and inline markup (*bold*, /italic/, ~code~, =verbatim=, _underline_, +strike+)
 * - Lists (unordered with -/+, ordered with 1.)
 * - Tables (|...| format with thead/tbody)
 * - Source blocks (#+BEGIN_SRC ... #+END_SRC with syntax highlighting)
 * - Quote blocks (#+BEGIN_QUOTE ... #+END_QUOTE)
 * - Example blocks (#+BEGIN_EXAMPLE ... #+END_EXAMPLE)
 * - Colon-prefixed example blocks (: ...)
 * - Links ([[file:][desc]], [[id:][desc]], [[*heading][desc]], http URLs)
 */
public class OrgRenderer {

	private static final int STATE_NORMAL = 0;
	private static final int STATE_TABLE = 1;
	private static final int STATE_SRC_BLOCK = 2;
	private static final int STATE_QUOTE = 3;
	private static final int STATE_EXAMPLE = 4;
	private static final int MAX_RECURSION_DEPTH = 50;

	private final ContentResolver resolver;
	private final String fontColor;

	public OrgRenderer(ContentResolver resolver, Context context) {
		this.resolver = resolver;
		String color = DefaultTheme.getTheme(context).defaultFontColor;
		// Convert named colors to hex for CSS compatibility
		if ("white".equals(color)) {
			color = "ffffff";
		} else if ("black".equals(color)) {
			color = "000000";
		}
		this.fontColor = color;
	}

	/**
	 * Pre-cleans the raw payload by stripping org-mode metadata blocks.
	 * Removes: :PROPERTIES:...:END:, :LOGBOOK:...:END:, SCHEDULED:/DEADLINE:/CLOSED: lines
	 * Preserves: ALL #+ lines (including #+BEGIN_SRC blocks)
	 */
	String preClean(String rawPayload) {
		if (rawPayload == null || rawPayload.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		String[] lines = rawPayload.split("\n");
		boolean inProperties = false;
		boolean inLogbook = false;

		for (String line : lines) {
			String trimmed = line.trim();

			if (trimmed.equals(":PROPERTIES:")) {
				inProperties = true;
				continue;
			}
			if (inProperties) {
				if (trimmed.startsWith(":") && !trimmed.equals(":END:")) {
					continue;
				}
				if (trimmed.equals(":END:")) {
					inProperties = false;
					continue;
				}
				inProperties = false;
			}

			if (trimmed.equals(":LOGBOOK:")) {
				inLogbook = true;
				continue;
			}
			if (inLogbook) {
				if (trimmed.equals(":END:")) {
					inLogbook = false;
					continue;
				}
				// All lines inside LOGBOOK block are skipped
				continue;
			}

			if (trimmed.startsWith("SCHEDULED:") ||
				trimmed.startsWith("DEADLINE:") ||
				trimmed.startsWith("CLOSED:")) {
				continue;
			}

			result.append(line).append("\n");
		}

		return result.toString();
	}

	/**
	 * Main rendering method - state machine that converts cleaned org text to HTML.
	 */
	String render(String cleanedPayload) {
		if (cleanedPayload == null || cleanedPayload.trim().isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		String[] lines = cleanedPayload.split("\n");

		int state = STATE_NORMAL;
		StringBuilder blockBuffer = new StringBuilder();
		String srcLang = "";
		boolean inUnorderedList = false;
		boolean inOrderedList = false;
		boolean exampleFromBlock = false;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String trimmed = line.trim();

			switch (state) {
				case STATE_NORMAL:
					if (trimmed.startsWith("#+BEGIN_SRC")) {
						closeListIfNeeded(result, inUnorderedList, inOrderedList);
						inUnorderedList = false;
						inOrderedList = false;
						state = STATE_SRC_BLOCK;
						String[] parts = trimmed.split("\\s+", 3);
						srcLang = parts.length > 1 ? mapLanguage(parts[1]) : "";
						blockBuffer = new StringBuilder();
						continue;
					}
					if (trimmed.startsWith("#+BEGIN_QUOTE")) {
						closeListIfNeeded(result, inUnorderedList, inOrderedList);
						inUnorderedList = false;
						inOrderedList = false;
						state = STATE_QUOTE;
						blockBuffer = new StringBuilder();
						continue;
					}
					if (trimmed.startsWith("#+BEGIN_EXAMPLE")) {
						closeListIfNeeded(result, inUnorderedList, inOrderedList);
						inUnorderedList = false;
						inOrderedList = false;
						state = STATE_EXAMPLE;
						exampleFromBlock = true;
						blockBuffer = new StringBuilder();
						continue;
					}

					if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
						closeListIfNeeded(result, inUnorderedList, inOrderedList);
						inUnorderedList = false;
						inOrderedList = false;
						state = STATE_TABLE;
						blockBuffer = new StringBuilder();
						blockBuffer.append(line).append("\n");
						continue;
					}

					if (!trimmed.isEmpty() && trimmed.startsWith(":")) {
						closeListIfNeeded(result, inUnorderedList, inOrderedList);
						inUnorderedList = false;
						inOrderedList = false;
						state = STATE_EXAMPLE;
						exampleFromBlock = false;
						blockBuffer = new StringBuilder();
						String exampleLine = line;
						if (exampleLine.startsWith(":")) {
							exampleLine = exampleLine.substring(1);
							if (exampleLine.startsWith(" ")) {
								exampleLine = exampleLine.substring(1);
							}
						}
						blockBuffer.append(exampleLine).append("\n");
						continue;
					}

					boolean isUnorderedItem = trimmed.matches("^[-+]\\s+.*");
					boolean isOrderedItem = trimmed.matches("^\\d+\\.\\s+.*");

					if (isUnorderedItem) {
						if (!inUnorderedList) {
							closeListIfNeeded(result, inUnorderedList, inOrderedList);
							result.append("<ul>\n");
							inUnorderedList = true;
							inOrderedList = false;
						}
						String content = htmlEncode(trimmed.substring(1).trim());
						content = applyInlineMarkup(content);
						content = convertLinks(content);
						result.append("<li>").append(content).append("</li>\n");
						break;
					}

					if (isOrderedItem) {
						if (!inOrderedList) {
							closeListIfNeeded(result, inUnorderedList, inOrderedList);
							result.append("<ol>\n");
							inOrderedList = true;
							inUnorderedList = false;
						}
						int dotIdx = trimmed.indexOf('.');
						String content = htmlEncode(trimmed.substring(dotIdx + 1).trim());
						content = applyInlineMarkup(content);
						content = convertLinks(content);
						result.append("<li>").append(content).append("</li>\n");
						break;
					}

					if (trimmed.isEmpty()) {
						result.append("<br/>\n");
						break;
					}

					if (inUnorderedList || inOrderedList) {
						closeListIfNeeded(result, inUnorderedList, inOrderedList);
						inUnorderedList = false;
						inOrderedList = false;
					}

					String processed = htmlEncode(line);
					processed = applyInlineMarkup(processed);
					processed = convertLinks(processed);
					result.append(processed).append("\n");
					break;

				case STATE_TABLE:
					blockBuffer.append(line).append("\n");
					if (trimmed.isEmpty() || !trimmed.startsWith("|")) {
						result.append(processTable(blockBuffer.toString()));
						state = STATE_NORMAL;
						blockBuffer = new StringBuilder();
					}
					break;

				case STATE_SRC_BLOCK:
					if (trimmed.startsWith("#+END_SRC")) {
						result.append("<pre class=\"src-block\"><code class=\"language-")
							  .append(srcLang)
							  .append("\">")
							  .append(htmlEncode(blockBuffer.toString()))
							  .append("</code></pre>\n");
						state = STATE_NORMAL;
						blockBuffer = new StringBuilder();
						srcLang = "";
					} else {
						blockBuffer.append(line).append("\n");
					}
					break;

				case STATE_QUOTE:
					if (trimmed.startsWith("#+END_QUOTE")) {
						String quoteContent = htmlEncode(blockBuffer.toString().trim());
						quoteContent = applyInlineMarkup(quoteContent);
						quoteContent = convertLinks(quoteContent);
						result.append("<blockquote>").append(quoteContent).append("</blockquote>\n");
						state = STATE_NORMAL;
						blockBuffer = new StringBuilder();
					} else {
						blockBuffer.append(line).append("\n");
					}
					break;

				case STATE_EXAMPLE:
					if (exampleFromBlock) {
						// #+BEGIN_EXAMPLE mode: collect until #+END_EXAMPLE
						if (trimmed.startsWith("#+END_EXAMPLE")) {
							if (blockBuffer.length() > 0) {
								result.append("<pre>")
									  .append(htmlEncode(blockBuffer.toString().trim()))
									  .append("</pre>\n");
							}
							state = STATE_NORMAL;
							exampleFromBlock = false;
							blockBuffer = new StringBuilder();
							continue;
						}
						blockBuffer.append(line).append("\n");
					} else {
						// Colon-prefixed mode: continue while lines start with ":"
						boolean stillExample = !trimmed.isEmpty() && trimmed.startsWith(":");

						if (stillExample) {
							if (line.startsWith(":")) {
								line = line.substring(1);
								if (line.startsWith(" ")) {
									line = line.substring(1);
								}
							}
							blockBuffer.append(line).append("\n");
						} else {
							if (blockBuffer.length() > 0) {
								result.append("<pre>")
									  .append(htmlEncode(blockBuffer.toString().trim()))
									  .append("</pre>\n");
							}
							state = STATE_NORMAL;
							blockBuffer = new StringBuilder();
							i--;
						}
					}
					break;
			}
		}

		closeListIfNeeded(result, inUnorderedList, inOrderedList);

		if (state == STATE_TABLE && blockBuffer.length() > 0) {
			result.append(processTable(blockBuffer.toString()));
		} else if (state == STATE_EXAMPLE && blockBuffer.length() > 0) {
			result.append("<pre>")
				  .append(htmlEncode(blockBuffer.toString().trim()))
				  .append("</pre>\n");
		}

		return result.toString().trim();
	}

	private void closeListIfNeeded(StringBuilder result, boolean inUl, boolean inOl) {
		if (inUl) result.append("</ul>\n");
		if (inOl) result.append("</ol>\n");
	}

	private String mapLanguage(String lang) {
		if (lang == null || lang.isEmpty()) return "";
		if (lang.equals("elisp") || lang.equals("emacs-lisp")) return "lisp";
		if (lang.equals("sh")) return "bash";
		return lang;
	}

	private String processTable(String tableText) {
		String[] lines = tableText.trim().split("\n");
		if (lines.length == 0) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		result.append("<table>\n");

		boolean firstRow = true;

		for (String line : lines) {
			String trimmed = line.trim();
			if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
				continue;
			}

			String content = trimmed.substring(1, trimmed.length() - 1);
			String[] cells = content.split("\\|");

			if (content.matches("^[-+|\\s]+$")) {
				continue;
			}

			if (firstRow) {
				result.append("<thead>\n<tr>");
				for (String cell : cells) {
					result.append("<th>")
						  .append(applyInlineMarkup(htmlEncode(cell.trim())))
						  .append("</th>");
				}
				result.append("</tr>\n</thead>\n<tbody>\n");
				firstRow = false;
			} else {
				result.append("<tr>");
				for (String cell : cells) {
					String cellContent = applyInlineMarkup(htmlEncode(cell.trim()));
					cellContent = convertLinks(cellContent);
					result.append("<td>").append(cellContent).append("</td>");
				}
				result.append("</tr>\n");
			}
		}

		result.append("</tbody>\n</table>\n");
		return result.toString();
	}

	String applyInlineMarkup(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		text = markupRegex("~", "code", text);
		text = markupRegex("=", "code", text);
		text = markupRegex("*", "b", text);
		text = markupRegex("/", "i", text);
		text = markupRegex("_", "u", text);
		text = markupRegex("+", "strike", text);

		return text;
	}

	private String markupRegex(String ch, String tag, String text) {
		return text.replaceAll(
			"(^|\\s)\\" + ch + "(\\S(?:[\\S\\s]*?\\S)?)\\" + ch + "(?=[\\s,.;:!?)\\]}>]|$)",
			"$1<" + tag + ">$2</" + tag + ">");
	}

	String convertLinks(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		text = text.replaceAll("\\[\\[file:([^\\]]+)\\]\\[([^\\]]+)\\]\\]",
							   "<a href=\"orgfile:$1\">$2</a>");
		text = text.replaceAll("\\[\\[id:([^\\]]+)\\]\\[([^\\]]+)\\]\\]",
							   "<a href=\"orgid:$1\">$2</a>");
		text = text.replaceAll("\\[\\[\\*([^\\]]+)\\]\\[([^\\]]+)\\]\\]",
							   "<a href=\"orginternal:*$1\">$2</a>");
		text = text.replaceAll("\\[\\[(https?://[^\\]]+)\\]\\[([^\\]]+)\\]\\]",
							   "<a href=\"$1\">$2</a>");
		text = text.replaceAll("(?<!<a href=\")(?<!\">)(https?://[^\\s<]+)",
							   "<a href=\"$1\">$1</a>");

		return text;
	}

	String wrapInTemplate(String htmlBody) {
		if (htmlBody == null || htmlBody.trim().isEmpty()) {
			htmlBody = "";
		}

		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">\n" +
			   "<link rel=\"stylesheet\" href=\"file:///android_asset/highlight/styles/atom-one-dark.css\">\n" +
			   "<script src=\"file:///android_asset/highlight/highlight.pack.js\"></script>\n" +
			   "<style>\n" +
			   "body { color: #" + fontColor + "; margin: 8px; }\n" +
			   "table { border-collapse: collapse; margin: 8px 0; }\n" +
			   "th, td { border: 1px solid #444; padding: 4px 8px; text-align: left; }\n" +
			   "th { font-weight: bold; }\n" +
			   "pre.src-block { background: #282c34; padding: 12px; border-radius: 4px; overflow-x: auto; }\n" +
			   "blockquote { border-left: 3px solid #666; margin: 8px 0; padding: 4px 12px; }\n" +
			   "ul, ol { padding-left: 20px; }\n" +
			   "li { margin: 2px 0; }\n" +
			   "code { background: #333; padding: 1px 4px; border-radius: 2px; }\n" +
			   "</style></head><body>\n" +
			   htmlBody + "\n" +
			   "<script>hljs.highlightAll();</script>\n" +
			   "</body></html>";
	}

	public String toHTML(OrgNode node, int levelOfRecursion) {
		String htmlBody = nodeToHTMLRecursive(node, levelOfRecursion, 0);
		return wrapInTemplate(htmlBody);
	}

	private String nodeToHTMLRecursive(OrgNode node, int level, int depth) {
		if (depth > MAX_RECURSION_DEPTH) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		result.append(nodeToHTML(node, level));

		if (level == 0) {
			for (OrgNode child : node.getChildren(resolver)) {
				result.append(nodeToHTMLRecursive(child, 0, depth + 1));
			}
		} else if (level > 0) {
			level--;
			for (OrgNode child : node.getChildren(resolver)) {
				result.append(nodeToHTMLRecursive(child, level, depth + 1));
			}
		}

		return result.toString();
	}

	private String nodeToHTML(OrgNode node, int headingLevel) {
		StringBuilder result = new StringBuilder();

		int fontSize = 3 + headingLevel;
		result.append("<font size=\"")
			  .append(fontSize)
			  .append("\"> <b>")
			  .append(htmlEncode(node.name))
			  .append("</b></font><hr/>\n");

		String payload = node.getPayload();
		if (payload != null && !payload.trim().isEmpty()) {
			String cleaned = preClean(payload);
			String rendered = render(cleaned);
			result.append(rendered).append("\n<br/>\n");
		}

		return result.toString();
	}

	public String payloadToHTML(OrgNode node) {
		String payload = node.getPayload();
		if (payload == null || payload.trim().isEmpty()) {
			return wrapInTemplate("");
		}

		String cleaned = preClean(payload);
		String rendered = render(cleaned);
		return wrapInTemplate(rendered);
	}

	private String htmlEncode(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;")
				   .replace("<", "&lt;")
				   .replace(">", "&gt;")
				   .replace("\"", "&quot;");
	}
}
