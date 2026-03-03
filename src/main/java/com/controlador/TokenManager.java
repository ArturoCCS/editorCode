package com.controlador;

import com.modelo.SyntaxTreeNodeRow;
import com.modelo.TokenRow;
import com.modelo.Tokenizer;
import com.modelo.SymbolRow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;

public class TokenManager {

    private final ObservableList<TokenRow> tokensObservable = FXCollections.observableArrayList();
    private final ObservableList<SymbolRow> symbolsObservable = FXCollections.observableArrayList();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<TokenEntry> tokens = new ArrayList<>();
    private LinkedHashMap<String, SymbolEntry> symbols = new LinkedHashMap<>();
    private List<Tokenizer.TokenError> lastErrors = new ArrayList<>();

    private final AtomicLong version = new AtomicLong(0);
    private long lastProcessedVersion = -1;

    private List<TokenRow> cachedTokenRows = new ArrayList<>();
    private List<SymbolRow> cachedSymbolRows = new ArrayList<>();
    private boolean tokenCacheValid = false;
    private boolean symbolCacheValid = false;

    private final ObservableList<SyntaxTreeNodeRow> syntaxTreeRows = FXCollections.observableArrayList();

    public ObservableList<SyntaxTreeNodeRow> getSyntaxTreeRows() {
        return syntaxTreeRows;
    }

    public void updateSyntaxTree(List<SyntaxTreeNodeRow> newRows) {
        List<SyntaxTreeNodeRow> sorted = new ArrayList<>(newRows);
        sorted.sort(Comparator.comparingInt(SyntaxTreeNodeRow::getNodeId));
        Platform.runLater(() -> {
            syntaxTreeRows.clear();
            syntaxTreeRows.addAll(sorted);
        });
    }

    public ObservableList<TokenRow> getTokensObservable() {
        return tokensObservable;
    }

    public ObservableList<SymbolRow> getSymbolsObservable() {
        return symbolsObservable;
    }

    public List<TokenEntry> getTokenEntries() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(tokens));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<SymbolEntry> getSymbolEntries() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new LinkedHashMap<>(symbols).values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Tokenizer.TokenError> getLastErrors() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(lastErrors));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateFromText(String text) {
        long currentVersion = version.incrementAndGet();
        ParseResult result = performParse(text);
        applyParseResult(result, currentVersion);
    }

    public void syncWithParseResult(List<TokenEntry> newTokens,
                                    List<Tokenizer.TokenError> newErrors,
                                    LinkedHashMap<String, SymbolEntry> newSymbols,
                                    long resultVersion) {
        if (resultVersion <= lastProcessedVersion) {
            return;
        }

        lock.writeLock().lock();
        try {
            this.tokens = new ArrayList<>(newTokens);
            this.lastErrors = new ArrayList<>(newErrors);
            this.symbols = new LinkedHashMap<>(newSymbols);

            tokenCacheValid = false;
            symbolCacheValid = false;

            lastProcessedVersion = resultVersion;

            Platform.runLater(this::updateObservableLists);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void syncWithParseResult(List<TokenEntry> tokens) {
        lock.readLock().lock();
        LinkedHashMap<String, SymbolEntry> currentSymbols;
        try {
            currentSymbols = new LinkedHashMap<>(this.symbols);
        } finally {
            lock.readLock().unlock();
        }

        syncWithParseResult(tokens, new ArrayList<>(), currentSymbols, version.get());
    }

    public ParseResult performParse(String text) {
        List<TokenEntry> newTokens = new ArrayList<>();
        List<Tokenizer.TokenError> newErrors = new ArrayList<>();
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();

        Tokenizer.scan(text, new Tokenizer.TokenSink() {
            @Override
            public void onToken(Tokenizer.Token t) {
                String tkName = mapToTkName(t.type, t.value);
                newTokens.add(new TokenEntry(t.value, tkName, t.start, t.end));
                if ("ID".equals(t.type)) uniqueIds.add(t.value);
            }

            @Override
            public void onError(Tokenizer.TokenError e) {
                newErrors.add(e);
            }
        });

        LinkedHashMap<String, SymbolEntry> newSymbols = new LinkedHashMap<>();

        lock.readLock().lock();
        try {
            for (String id : uniqueIds) {
                SymbolEntry prev = symbols.get(id);
                newSymbols.put(id, prev != null ? prev : new SymbolEntry(id, "", ""));
            }
        } finally {
            lock.readLock().unlock();
        }

        return new ParseResult(newTokens, newErrors, newSymbols, version.incrementAndGet());
    }

    private void applyParseResult(ParseResult result, long resultVersion) {
        lock.writeLock().lock();
        try {
            this.tokens = new ArrayList<>(result.tokens);
            this.lastErrors = new ArrayList<>(result.errors);
            this.symbols = new LinkedHashMap<>(result.symbols);

            tokenCacheValid = false;
            symbolCacheValid = false;

            lastProcessedVersion = resultVersion;
        } finally {
            lock.writeLock().unlock();
        }

        updateObservableLists();
    }

    private void updateObservableLists() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateObservableLists);
            return;
        }

        lock.readLock().lock();
        try {
            if (!tokenCacheValid) {
                cachedTokenRows = mapTokenRows(tokens);
                tokenCacheValid = true;
            }

            if (!symbolCacheValid) {
                cachedSymbolRows = mapSymbolRows(symbols.values());
                symbolCacheValid = true;
            }

            tokensObservable.setAll(cachedTokenRows);
            symbolsObservable.setAll(cachedSymbolRows);
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<TokenRow> mapTokenRows(List<TokenEntry> list) {
        List<TokenRow> rows = new ArrayList<>();
        for (TokenEntry e : list) rows.add(new TokenRow(e.lexema(), e.token()));
        return rows;
    }

    private List<SymbolRow> mapSymbolRows(Collection<SymbolEntry> entries) {
        List<SymbolRow> rows = new ArrayList<>();
        for (SymbolEntry e : entries) rows.add(new SymbolRow(e.id(), e.tipo(), e.valor()));
        return rows;
    }

    private String mapToTkName(String type, String lexema) {
        switch (type) {
            case "PR": {
                String upper = lexema.toUpperCase(Locale.ROOT);
                return switch (upper) {
                    case "INTEGER" -> "TK_tipoEntero";
                    case "DECIMAL" -> "TK_tipoDecimal";
                    default -> "TK_" + upper;
                };
            }
            case "ID":
                return "TK_ID";
            case "ENT":
                return "TK_ENT";
            case "DEC":
                return "TK_DEC";
            case "SIMB":
                return "TK_" + lexema;
            default:
                return "TK_" + type;
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            tokens.clear();
            symbols.clear();
            lastErrors.clear();
            tokenCacheValid = false;
            symbolCacheValid = false;
            version.incrementAndGet();

            Platform.runLater(() -> {
                tokensObservable.clear();
                symbolsObservable.clear();
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getCurrentVersion() {
        return version.get();
    }

    public record ParseResult(
            List<TokenEntry> tokens,
            List<Tokenizer.TokenError> errors,
            LinkedHashMap<String, SymbolEntry> symbols,
            long version
    ) {}

    public record TokenEntry(String lexema, String token, int start, int end) {}
    public record SymbolEntry(String id, String tipo, String valor) {}
}