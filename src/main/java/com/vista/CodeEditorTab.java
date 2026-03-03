package com.vista;

import com.controlador.TokenManager;
import com.modelo.RecursiveDescentParser;
import com.modelo.SyntaxTreeNodeRow;
import com.modelo.lr.ShiftReduceParseTreeBuilder;
import com.modelo.Tokenizer;
import com.modelo.ast.ASTNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class CodeEditorTab {

    private final BorderPane root;
    private final CodeArea codeArea;
    private final String title;
    private MascotCrocSprite mascot;

    private final TokenManager tokenManager;

    private final PauseTransition parseDebounce = new PauseTransition(Duration.millis(150));
    private final AtomicLong parseVersion = new AtomicLong(0);

    private Thread parseThread;
    private volatile boolean isParsing = false;

    private final Map<Integer, PauseTransition> keyFxMap = new HashMap<>();
    private List<Tokenizer.TokenError> lastErrorsLexi = new ArrayList<>();
    private List<RecursiveDescentParser.ParseError> lastErrorsSynt = new ArrayList<>();
    private final RecursiveDescentParser recursiveParser = new RecursiveDescentParser();

    private final PauseTransition errorHoverDelay = new PauseTransition(Duration.millis(220));
    private HoverError hoveredError = null;

    private final Set<Integer> errorLines = new HashSet<>();

    private String lastProcessedText = "";
    private List<TokenManager.TokenEntry> lastProcessedTokens = new ArrayList<>();

    private ASTNode rootAST;

    private static final class HoverError {
        final int start;
        final int end;
        final String message;

        HoverError(int start, int end, String message) {
            this.start = start;
            this.end = end;
            this.message = message;
        }
    }

    private static class ParseResult {
        final List<TokenManager.TokenEntry> tokens;
        final List<Tokenizer.TokenError> lexErrors;
        final List<RecursiveDescentParser.ParseError> syntErrors;
        final ASTNode ast;
        final List<SyntaxTreeNodeRow> syntaxRows;
        final long version;

        ParseResult(List<TokenManager.TokenEntry> tokens,
                    List<Tokenizer.TokenError> lexErrors,
                    List<RecursiveDescentParser.ParseError> syntErrors,
                    ASTNode ast,
                    List<SyntaxTreeNodeRow> syntaxRows,
                    long version) {
            this.tokens = tokens;
            this.lexErrors = lexErrors;
            this.syntErrors = syntErrors;
            this.ast = ast;
            this.syntaxRows = syntaxRows;
            this.version = version;
        }
    }

    public CodeEditorTab(String title, AudioClip baseKeySound, TokenManager tokenManager) {
        this.title = title;
        this.root = new BorderPane();
        this.tokenManager = tokenManager;

        codeArea = new CodeArea();
        codeArea.setWrapText(false);

        setupGutter();
        setupCodeArea();
        setupErrorTooltip();
        setupMascotOverlayViewportBound();

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            keyFxMap.values().forEach(PauseTransition::stop);
            keyFxMap.clear();

            parseVersion.incrementAndGet();

            parseDebounce.setOnFinished(e -> scheduleParse(newText));
            parseDebounce.playFromStart();

            hoveredError = null;
        });

        initializeContent();
    }

    private void setupGutter() {
        codeArea.setParagraphGraphicFactory(idx -> {
            HBox gutter = new HBox();
            gutter.setAlignment(Pos.CENTER_LEFT);
            gutter.getStyleClass().add("gutter");
            gutter.setSpacing(6);

            Label lineNo = new Label(String.valueOf(idx + 1));
            lineNo.getStyleClass().add("lineno");

            Region errorMarker = new Region();
            errorMarker.getStyleClass().add("line-error-marker");
            boolean hasError = errorLines.contains(idx);
            errorMarker.setVisible(hasError);
            errorMarker.setManaged(hasError);

            gutter.getChildren().addAll(lineNo, errorMarker);
            HBox.setMargin(lineNo, new Insets(0, 0, 0, 8));
            return gutter;
        });
    }

    private void setupCodeArea() {
        codeArea.getStyleClass().add("code-area");
        codeArea.replaceText("");
        codeArea.setOnMouseEntered(ev -> codeArea.requestFocus());
        codeArea.addEventHandler(KeyEvent.KEY_TYPED, this::handleKeyTypedDisabledCombo);

        try {
            codeArea.getStylesheets().add(getClass().getResource("/com/editorcode/styles.css").toExternalForm());
        } catch (Exception ignore) {}
    }

    private void setupMascotOverlayViewportBound() {
        mascot = new MascotCrocSprite();
        mascot.setMouseTransparent(true);
        mascot.setPickOnBounds(false);

        VirtualizedScrollPane<CodeArea> scroller = new VirtualizedScrollPane<>(codeArea);

        StackPane viewport = new StackPane();
        viewport.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane.setAlignment(scroller, Pos.TOP_LEFT);
        viewport.getChildren().add(scroller);

        Pane overlay = new Pane();
        overlay.setPickOnBounds(false);
        overlay.setMouseTransparent(true);
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        overlay.prefWidthProperty().bind(viewport.widthProperty());
        overlay.prefHeightProperty().bind(viewport.heightProperty());

        mascot.layoutXProperty().bind(overlay.widthProperty().subtract(mascot.widthProperty()).subtract(18));
        mascot.layoutYProperty().bind(overlay.heightProperty().subtract(mascot.heightProperty()).subtract(10));

        overlay.getChildren().add(mascot);

        StackPane container = new StackPane(viewport, overlay);
        container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        root.setCenter(container);
    }

    private void initializeContent() {
        tokenManager.updateFromText(codeArea.getText());
        updateSyntaxStylesFromModel();
        updateErrorHighlightsFromModelLexi();
    }

    private void scheduleParse(String text) {
        long currentVersion = parseVersion.incrementAndGet();

        if (isParsing && parseThread != null) {
            parseThread.interrupt();
        }

        ParseTask task = new ParseTask(text, currentVersion, tokenManager, recursiveParser);

        task.setOnRunning(e -> isParsing = true);

        task.setOnSucceeded(e -> {
            isParsing = false;
            ParseResult result = task.getValue();
            if (result != null && result.version == parseVersion.get()) {
                Platform.runLater(() -> updateUIWithParseResult(result));
            }
        });

        task.setOnFailed(e -> {
            isParsing = false;
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        parseThread = new Thread(task);
        parseThread.setDaemon(true);
        parseThread.start();
    }

    private static class ParseTask extends Task<ParseResult> {
        private final String text;
        private final long version;
        private final TokenManager tokenManager;
        private final RecursiveDescentParser recursiveParser;

        ParseTask(String text, long version, TokenManager tokenManager, RecursiveDescentParser recursiveParser) {
            this.text = text;
            this.version = version;
            this.tokenManager = tokenManager;
            this.recursiveParser = recursiveParser;
        }

        @Override
        protected ParseResult call() {
            if (isCancelled()) return null;

            tokenManager.updateFromText(text);

            if (isCancelled()) return null;

            List<Tokenizer.TokenError> lexErrors = new ArrayList<>(tokenManager.getLastErrors());

            List<RecursiveDescentParser.ParseError> syntErrors = new ArrayList<>();
            ASTNode ast = null;
            List<SyntaxTreeNodeRow> syntaxRows = Collections.emptyList();

            if (lexErrors.isEmpty()) {
                ast = recursiveParser.parse(tokenManager.getTokenEntries());
                syntErrors.addAll(recursiveParser.getErrores());
            }

            if (!text.trim().isEmpty() && lexErrors.isEmpty() && syntErrors.isEmpty()) {
                syntaxRows = new ShiftReduceParseTreeBuilder().build(tokenManager.getTokenEntries());
            }

            if (isCancelled()) return null;

            return new ParseResult(
                    new ArrayList<>(tokenManager.getTokenEntries()),
                    lexErrors,
                    syntErrors,
                    ast,
                    syntaxRows,
                    version
            );
        }
    }

    private void updateUIWithParseResult(ParseResult result) {
        String currentText = codeArea.getText();
        if (!currentText.equals(lastProcessedText)) {
            lastErrorsLexi = result.lexErrors;
            lastErrorsSynt = result.syntErrors;
            rootAST = result.ast;

            tokenManager.syncWithParseResult(result.tokens);
            updateSyntaxStylesFromModel();
            updateErrorHighlightsFromModelLexi();
            updateErrorHighlightsFromModelSynt();
            tokenManager.updateSyntaxTree(result.syntaxRows);

            lastProcessedText = currentText;
            lastProcessedTokens = new ArrayList<>(result.tokens);
        }
    }

    private void updateSyntaxStylesFromModel() {
        List<TokenManager.TokenEntry> currentTokens = tokenManager.getTokenEntries();
        if (currentTokens.equals(lastProcessedTokens)) return;

        codeArea.clearStyle(0, codeArea.getLength());

        for (TokenManager.TokenEntry e : currentTokens) {
            String tk = e.token();
            String lx = e.lexema();

            String styleClass = switch (tk) {
                case "TK_ID" -> "tok-id";
                case "TK_ENT" -> "tok-num";
                case "TK_DEC" -> "tok-dec";
                default -> {
                    if (tk.startsWith("TK_tipo")) yield "tok-type";
                    else if ("TK_START".equals(tk) || "TK_END".equals(tk)) yield "tok-kw";
                    else if ("TK_READ".equals(tk) || "TK_PRINT".equals(tk)) yield "tok-io";
                    else if (tk.startsWith("TK_") && lx != null && lx.length() == 1 && isSymbolChar(lx.charAt(0))) yield "tok-sym";
                    else yield "tok-pr";
                }
            };

            codeArea.setStyleClass(e.start(), e.end(), styleClass);
        }

        lastProcessedTokens = new ArrayList<>(currentTokens);
    }

    private void updateErrorHighlightsFromModelLexi() {
        updateErrorHighlightsCombined();
    }

    private void updateErrorHighlightsFromModelSynt() {
        updateErrorHighlightsCombined();
    }

    private void updateErrorHighlightsCombined() {
        Set<Integer> newErrorLines = new HashSet<>();

        for (Tokenizer.TokenError err : lastErrorsLexi) {
            int start = Math.max(0, Math.min(err.start, codeArea.getLength()));
            int end = Math.max(0, Math.min(err.end, codeArea.getLength()));
            if (end <= start) end = Math.min(codeArea.getLength(), start + 1);

            TwoDimensional.Position posStart = codeArea.offsetToPosition(start, TwoDimensional.Bias.Forward);
            newErrorLines.add(posStart.getMajor());
            codeArea.setStyleClass(start, end, "error-lex");
        }

        for (RecursiveDescentParser.ParseError err : lastErrorsSynt) {
            int start = Math.max(0, Math.min(err.start, codeArea.getLength()));
            int end = Math.max(0, Math.min(err.end, codeArea.getLength()));
            if (end < start) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            if (end <= start) end = Math.min(codeArea.getLength(), start + 1);

            if (start < end) {
                TwoDimensional.Position posStart = codeArea.offsetToPosition(start, TwoDimensional.Bias.Forward);
                newErrorLines.add(posStart.getMajor());
                codeArea.setStyleClass(start, end, "error-syn");
            }
        }

        errorLines.clear();
        errorLines.addAll(newErrorLines);

        codeArea.setParagraphGraphicFactory(codeArea.getParagraphGraphicFactory());

        updateSyntaxStylesFromModel();
    }

    private void handleKeyTypedDisabledCombo(KeyEvent ev) {
        int idx = codeArea.getCaretPosition() - 1;
        if (idx < 0 || idx >= codeArea.getLength()) return;
        String ch = codeArea.getText(idx, idx + 1);
        if (ch != null && ch.length() == 1 && !Character.isWhitespace(ch.charAt(0))) {
            highlightAnimatedChar(idx);
        }
    }

    private HoverError getHoverErrorAtIndex(int idx) {
        Tokenizer.TokenError lex = getLexErrorAtIndex(idx);
        if (lex != null) return new HoverError(lex.start, lex.end, lex.message);

        RecursiveDescentParser.ParseError syn = getSyntErrorAtIndex(idx);
        if (syn != null) return new HoverError(syn.start, syn.end, syn.message);

        return null;
    }

    private Tokenizer.TokenError getLexErrorAtIndex(int idx) {
        for (Tokenizer.TokenError err : lastErrorsLexi) {
            if (idx >= err.start && idx < err.end) return err;
        }
        return null;
    }

    private RecursiveDescentParser.ParseError getSyntErrorAtIndex(int idx) {
        RecursiveDescentParser.ParseError best = null;
        int bestLen = Integer.MAX_VALUE;

        for (RecursiveDescentParser.ParseError err : lastErrorsSynt) {
            if (idx >= err.start && idx < err.end) {
                int len = err.end - err.start;
                if (len < bestLen) {
                    bestLen = len;
                    best = err;
                }
            }
        }
        return best;
    }

    private boolean hoverErrorsEqual(HoverError a, HoverError b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.start == b.start && a.end == b.end && Objects.equals(a.message, b.message);
    }

    private void highlightAnimatedChar(int idx) {
        if (idx < 0 || idx >= codeArea.getLength()) return;
        codeArea.setStyleClass(idx, idx + 1, "combo-key");
        PauseTransition pt = new PauseTransition(Duration.millis(120));
        keyFxMap.put(idx, pt);
        pt.setOnFinished(e -> {
            if (idx >= 0 && idx < codeArea.getLength()) {
                codeArea.setStyleClass(idx, idx + 1, "");
            }
            keyFxMap.remove(idx);
        });
        pt.play();
    }

    private boolean isSymbolChar(char c) {
        return c == '{' || c == '}' || c == '(' || c == ')' ||
                c == ';' || c == ',' || c == '.' || c == '=' ||
                c == '+' || c == '-' || c == '*' || c == '/';
    }

    private void showMascotErrorForHover(HoverError err) {
        if (err == null) return;

        int lineNumber = getLineNumberFromOffset(err.start);
        String context = getContextAroundError(err);

        String message = formatErrorMessage(err, lineNumber, context);

        MascotCrocSprite.MessageType type = err.message.toLowerCase(Locale.ROOT).contains("error") ?
                MascotCrocSprite.MessageType.ERROR :
                MascotCrocSprite.MessageType.INFO;

        mascot.showMessage(message, type);
        highlightErrorTemporarily(err);
    }

    private int getLineNumberFromOffset(int offset) {
        TwoDimensional.Position pos = codeArea.offsetToPosition(offset, TwoDimensional.Bias.Forward);
        return pos.getMajor() + 1;
    }

    private String getContextAroundError(HoverError err) {
        TwoDimensional.Position pos = codeArea.offsetToPosition(err.start, TwoDimensional.Bias.Forward);
        int lineIdx = pos.getMajor();

        String line = codeArea.getText(lineIdx);
        if (line.length() > 30) {
            int col = pos.getMinor();
            int start = Math.max(0, col - 15);
            int end = Math.min(line.length(), col + 15);
            return "..." + line.substring(start, end) + "...";
        }
        return line;
    }

    private String formatErrorMessage(HoverError err, int lineNumber, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Línea ").append(lineNumber).append(":\n");
        sb.append(context).append("\n");
        sb.append(err.message);
        return sb.toString();
    }

    private void highlightErrorTemporarily(HoverError err) {
        codeArea.setStyleClass(err.start, err.end, "error-hover-highlight");

        PauseTransition pt = new PauseTransition(Duration.seconds(2));
        pt.setOnFinished(e -> {
            if (err.start >= 0 && err.end <= codeArea.getLength()) {
                if (isPositionInError(err.start)) {
                    codeArea.setStyleClass(err.start, err.end, "error-highlight");
                } else {
                    updateSyntaxStylesFromModel();
                }
            }
        });
        pt.play();
    }

    private boolean isPositionInError(int pos) {
        for (Tokenizer.TokenError err : lastErrorsLexi) {
            if (pos >= err.start && pos < err.end) return true;
        }
        for (RecursiveDescentParser.ParseError err : lastErrorsSynt) {
            if (pos >= err.start && pos < err.end) return true;
        }
        return false;
    }

    private void setupErrorTooltip() {
        errorHoverDelay.setDuration(Duration.millis(200));

        codeArea.setOnMouseMoved(ev -> {
            int idx = codeArea.hit(ev.getX(), ev.getY()).getInsertionIndex();
            HoverError err = getHoverErrorAtIndex(idx);

            if (err != null) {
                if (!hoverErrorsEqual(err, hoveredError)) {
                    hoveredError = err;
                    errorHoverDelay.stop();
                    errorHoverDelay.setOnFinished(e -> {
                        showMascotErrorForHover(err);
                        mascot.keepMessageVisible();
                    });
                    errorHoverDelay.playFromStart();
                } else {
                    mascot.keepMessageVisible();
                }
            } else {
                hoveredError = null;
                errorHoverDelay.stop();
                mascot.enableAutoHideAndRestart();
            }
        });

        codeArea.setOnMouseExited(ev -> {
            hoveredError = null;
            errorHoverDelay.stop();
            mascot.enableAutoHideAndRestart();
        });
    }

    public Node getNode() { return root; }
    public CodeArea getCodeArea() { return codeArea; }
}