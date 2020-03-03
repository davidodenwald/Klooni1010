package dev.lonami.klooni.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Stack;

import dev.lonami.klooni.Klooni;
import dev.lonami.klooni.SkinLoader;
import dev.lonami.klooni.serializer.BinSerializable;

// Undoer can undo the last move from the current hand.
public class Undoer implements BinSerializable {

    private Board board;
    private PieceHolder pieceHolder;
    private BaseScorer scorer;

    private Stack<State> states;

    final Texture undoButton;
    final Rectangle undoArea;
    private Color undoColor;

    public Undoer(GameLayout layout, Board board, PieceHolder pieceHolder, BaseScorer scorer) {
        this.board = board;
        this.pieceHolder = pieceHolder;
        this.scorer = scorer;

        states = new Stack<State>();

        undoButton = SkinLoader.loadPng("undo.png");
        undoColor = Klooni.theme.bandColor.cpy();
        undoArea = new Rectangle();

        layout.update(this);
    }

    // returns true when a undo is possible.
    private boolean canUndo() {
        return pieceHolder.getAvailablePieces().size < pieceHolder.count;
    }

    public void draw(SpriteBatch batch) {
        batch.setColor(undoColor);
        if (canUndo()) {
            batch.draw(undoButton, undoArea.x, undoArea.y, undoArea.width, undoArea.height);
        }
    }

    // Records the game state (score, board-cells, pieceholder-items).
    public void recordState() {
        if (!canUndo()) {
            // clear states when hand is refilled
            clearStates();
        }

        Cell[][] cellsCpy = new Cell[board.cellCount][board.cellCount];
        for (int i = 0; i < board.cellCount; ++i) {
            for (int j = 0; j < board.cellCount; ++j) {
                cellsCpy[i][j] = new Cell(j * board.cellSize, i * board.cellSize, board.cellSize);
                cellsCpy[i][j].set(board.cells[i][j].colorIndex);
            }
        }
        Piece[] piecesCpy = new Piece[pieceHolder.count];
        System.arraycopy(pieceHolder.pieces, 0, piecesCpy, 0, pieceHolder.count);

        states.push(new State(scorer.currentScore, cellsCpy, piecesCpy));
    }

    // Removes the state that was recorded last.
    public void discardLastState() {
        states.pop();
    }

    // Removes all recorded states.
    private void clearStates() {
        states.clear();
    }

    // Undo the last move if the undo icon was pressed.
    // returns true if the undo icon was pressed; false otherwise.
    public boolean onPress() {
        int x = Gdx.input.getX();
        int y = Gdx.graphics.getHeight() - Gdx.input.getY();

        // check if the undo icon was pressed and undo is possible
        if (x >= undoArea.x && x <= undoArea.x + undoArea.width
                && y >= undoArea.y && y <= undoArea.y + undoArea.height
                && canUndo()) {
            undoLastMove();
            return true;
        }
        return false;
    }

    private void undoLastMove() {
        State state = states.pop();
        scorer.currentScore = state.score;
        board.cells = state.cells;
        System.arraycopy(state.pieces, 0, pieceHolder.pieces, 0, pieceHolder.count);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        int statesLen = states.size();
        out.writeInt(statesLen);

        // iterate from the bottom to the top of the Stack to get the correct order when reading later.
        for (int i = 0; i < statesLen; i++) {
            states.get(i).write(out);
        }
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        final int savedStates = in.readInt();

        for (int i = 0; i < savedStates; i++) {
            State state = new State();
            state.read(in);
            states.push(state);
        }
    }

    public class State implements BinSerializable {
        int score;
        Cell[][] cells;
        Piece[] pieces;

        State() {
        }

        State(int score, Cell[][] cells, Piece[] pieces) {
            this.score = score;
            this.cells = cells;
            this.pieces = pieces;
        }

        @Override
        public void write(DataOutputStream out) throws IOException {
            out.writeInt(score);

            for (int i = 0; i < board.cellCount; ++i) {
                for (int j = 0; j < board.cellCount; ++j) {
                    cells[i][j].write(out);
                }
            }

            for (int i = 0; i < pieceHolder.count; ++i) {
                if (pieces[i] == null) {
                    out.writeBoolean(false);
                } else {
                    out.writeBoolean(true);
                    pieces[i].write(out);
                }
            }
        }

        @Override
        public void read(DataInputStream in) throws IOException {
            score = in.readInt();

            cells = new Cell[board.cellCount][board.cellCount];
            for (int i = 0; i < board.cellCount; ++i) {
                for (int j = 0; j < board.cellCount; ++j) {
                    cells[i][j] = new Cell(j * board.cellSize, i * board.cellSize, board.cellSize);
                    cells[i][j].read(in);
                }
            }

            pieces = new Piece[pieceHolder.count];
            for (int i = 0; i < pieceHolder.count; i++)
                pieces[i] = in.readBoolean() ? Piece.read(in) : null;
        }
    }
}
