package stud.g10;

import core.player.AI;
import core.board.Board;
import core.board.PieceColor;
import core.game.Move;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AI_V1 extends AI {

    private static final int BOARD_SIZE = 19;
    private static final char CENTER = 'J';
    private static final int WIN_LENGTH = 6;
    private static final int THREAT_LENGTH = 5; // 五连是威胁

    // 8个方向向量
    private static final int[][] DIRECTIONS = {
            {1, 0},   // 水平
            {0, 1},   // 垂直
            {1, 1},   // 主对角线
            {1, -1}   // 副对角线
    };

    // 用于模拟的临时变量
    private Board simulatedBoard;

    @Override
    public String name() {
        return "G10-V1";
    }

    @Override
    public String fullName() {
        return this.name() + " (" + this._myColor + ")";
    }

    @Override
    protected Move findNextMove(Move opponentMove) {
        if (board == null) {
            return createSafeDefaultMove();
        }

        // 1. 检查是否有立即获胜的走法
        Move winningMove = findWinningMove();
        if (winningMove != null) {
            System.out.println("AI V1: Found winning move!");
            return winningMove;
        }

        // 2. 检查对方威胁并防守
        Move defensiveMove = findDefensiveMove();
        if (defensiveMove != null) {
            System.out.println("AI V1: Making defensive move.");
            return defensiveMove;
        }

        // 3. 智能选点
        List<Move> candidateMoves = generateSmartMoves(15);
        if (!candidateMoves.isEmpty()) {
            Move bestMove = selectBestMove(candidateMoves);
            System.out.println("AI V1: Making smart move.");
            return bestMove;
        }

        // 4. 保障走法
        System.out.println("AI V1: Using guaranteed move.");
        return createGuaranteedMove();
    }

    /**
     * 1. 检查是否有立即获胜的走法
     */
    private Move findWinningMove() {
        List<int[]> emptySpots = getAllEmptySpots();

        // 检查所有可能的双落子组合
        for (int i = 0; i < emptySpots.size(); i++) {
            int[] spot1 = emptySpots.get(i);
            for (int j = i + 1; j < emptySpots.size(); j++) {
                int[] spot2 = emptySpots.get(j);

                Move move = createMove(spot1[0], spot1[1], spot2[0], spot2[1]);
                if (move != null && board.legalMove(move)) {
                    // 使用模拟棋盘检查是否获胜
                    if (simulateMoveAndCheckWin(move, _myColor)) {
                        return move;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 2. 防守对方的威胁（对方威胁不多于两个时，能够防守得住）
     */
    private Move findDefensiveMove() {
        PieceColor opponentColor = (_myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;

        // 查找对方的威胁位置
        List<Threat> opponentThreats = findAllThreats(opponentColor);

        System.out.println("AI V1: Found " + opponentThreats.size() + " opponent threats");

        if (opponentThreats.size() <= 2) {
            // 威胁不多于两个，尝试防守
            for (Threat threat : opponentThreats) {
                // 尝试堵住威胁的端点
                Move blockingMove = createBlockingMove(threat);
                if (blockingMove != null && board.legalMove(blockingMove)) {
                    System.out.println("AI V1: Blocking threat at (" + threat.row + "," + threat.col + ")");
                    return blockingMove;
                }
            }

            // 如果单一防守失败，尝试同时防守多个威胁
            if (opponentThreats.size() == 2) {
                Move doubleDefense = createDoubleDefenseMove(opponentThreats);
                if (doubleDefense != null && board.legalMove(doubleDefense)) {
                    System.out.println("AI V1: Using double defense");
                    return doubleDefense;
                }
            }
        }

        return null;
    }

    /**
     * 3. 生成智能候选走法
     */
    private List<Move> generateSmartMoves(int count) {
        List<Move> moves = new ArrayList<>();
        List<int[]> emptySpots = getAllEmptySpots();

        // 如果空位太少，直接返回
        if (emptySpots.size() < 2) {
            return moves;
        }

        // 根据评分选择候选点
        List<ScoredSpot> scoredSpots = new ArrayList<>();
        for (int[] spot : emptySpots) {
            int score = evaluateSpot(spot[0], spot[1]);
            scoredSpots.add(new ScoredSpot(spot[0], spot[1], score));
        }

        // 按评分排序
        scoredSpots.sort((a, b) -> b.score - a.score);

        // 选择前N个点生成走法
        int limit = Math.min(scoredSpots.size(), Math.min(count * 3, 30));
        for (int i = 0; i < limit && moves.size() < count; i++) {
            for (int j = i + 1; j < limit && moves.size() < count; j++) {
                ScoredSpot s1 = scoredSpots.get(i);
                ScoredSpot s2 = scoredSpots.get(j);

                // 确保两个点不是同一个位置
                if (s1.row == s2.row && s1.col == s2.col) continue;

                Move move = createMove(s1.row, s1.col, s2.row, s2.col);
                if (move != null && board.legalMove(move)) {
                    moves.add(move);
                }
            }
        }

        // 如果候选走法不够，补充一些随机走法
        if (moves.size() < count && emptySpots.size() >= 2) {
            int needed = count - moves.size();
            for (int k = 0; k < needed * 2 && k < emptySpots.size() * (emptySpots.size() - 1) / 2; k++) {
                int i = k % emptySpots.size();
                int j = (k + 1) % emptySpots.size();
                if (i == j) j = (j + 1) % emptySpots.size();

                int[] spot1 = emptySpots.get(i);
                int[] spot2 = emptySpots.get(j);
                Move move = createMove(spot1[0], spot1[1], spot2[0], spot2[1]);
                if (move != null && board.legalMove(move)) {
                    moves.add(move);
                }
            }
        }

        return moves;
    }

    /**
     * 评估单个位置的分数
     */
    private int evaluateSpot(int row, int col) {
        int score = 0;

        // 1. 中心性
        int centerDist = Math.abs(row - (CENTER - 'A')) + Math.abs(col - (CENTER - 'A'));
        score += (20 - centerDist) * 2;

        // 2. 靠近己方棋子
        score += countNearbyFriends(row, col) * 3;

        // 3. 远离对方棋子
        score -= countNearbyOpponents(row, col) * 2;

        // 4. 在棋盘中位置的价值
        if (row >= 4 && row <= 14 && col >= 4 && col <= 14) {
            score += 10; // 中心区域
        }

        // 5. 是否在边界（边界价值较低）
        if (row == 0 || row == BOARD_SIZE - 1 || col == 0 || col == BOARD_SIZE - 1) {
            score -= 5;
        }

        return score;
    }

    /**
     * 模拟走法并检查是否获胜
     */
    private boolean simulateMoveAndCheckWin(Move move, PieceColor color) {
        // 创建棋盘副本进行模拟
        simulatedBoard = new Board(board);

        // 在模拟棋盘上执行走法
        simulatedBoard.makeMove(move);

        // 检查是否获胜
        boolean win = checkColorWinOnBoard(simulatedBoard, color);

        // 清理模拟棋盘
        simulatedBoard = null;

        return win;
    }

    /**
     * 在指定棋盘上检查指定颜色是否获胜
     */
    private boolean checkColorWinOnBoard(Board boardToCheck, PieceColor color) {
        // 检查所有位置
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (boardToCheck.get((char)('A' + c), (char)('A' + r)) == color) {
                    // 检查4个方向
                    for (int[] dir : DIRECTIONS) {
                        if (countInDirectionOnBoard(boardToCheck, r, c, dir[0], dir[1], color) >= WIN_LENGTH) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 在指定棋盘上计算指定方向上的连续棋子数
     */
    private int countInDirectionOnBoard(Board boardToCheck, int startRow, int startCol, int dr, int dc, PieceColor color) {
        int count = 0;
        int r = startRow;
        int c = startCol;

        // 正向计数
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE &&
                boardToCheck.get((char)('A' + c), (char)('A' + r)) == color) {
            count++;
            r += dr;
            c += dc;
        }

        // 反向计数（不包括起点）
        r = startRow - dr;
        c = startCol - dc;
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE &&
                boardToCheck.get((char)('A' + c), (char)('A' + r)) == color) {
            count++;
            r -= dr;
            c -= dc;
        }

        return count;
    }

    /**
     * 查找所有的威胁
     */
    private List<Threat> findAllThreats(PieceColor color) {
        List<Threat> threats = new ArrayList<>();
        Set<String> threatKeys = new HashSet<>(); // 用于去重

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board.get((char)('A' + c), (char)('A' + r)) == color) {
                    // 检查4个方向
                    for (int[] dir : DIRECTIONS) {
                        Threat threat = findThreatInDirection(r, c, dir[0], dir[1], color);
                        if (threat != null) {
                            String key = threat.row + "," + threat.col + "," + threat.dr + "," + threat.dc;
                            if (!threatKeys.contains(key)) {
                                threats.add(threat);
                                threatKeys.add(key);
                            }
                        }
                    }
                }
            }
        }

        return threats;
    }

    /**
     * 在指定方向上查找威胁
     */
    private Threat findThreatInDirection(int startRow, int startCol, int dr, int dc, PieceColor color) {
        // 向两个方向探索
        int emptyCount = 0;
        int pieceCount = 0;
        List<int[]> emptySpots = new ArrayList<>();

        // 正向探索
        int r = startRow;
        int c = startCol;
        while (pieceCount + emptyCount < THREAT_LENGTH) {
            if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) break;

            PieceColor current = board.get((char)('A' + c), (char)('A' + r));
            if (current == color) {
                pieceCount++;
            } else if (current == PieceColor.EMPTY) {
                emptyCount++;
                emptySpots.add(new int[]{r, c});
            } else {
                break; // 遇到对方棋子
            }
            r += dr;
            c += dc;
        }

        // 反向探索
        r = startRow - dr;
        c = startCol - dc;
        while (pieceCount + emptyCount < THREAT_LENGTH) {
            if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) break;

            PieceColor current = board.get((char)('A' + c), (char)('A' + r));
            if (current == color) {
                pieceCount++;
            } else if (current == PieceColor.EMPTY) {
                emptyCount++;
                emptySpots.add(new int[]{r, c});
            } else {
                break;
            }
            r -= dr;
            c -= dc;
        }

        // 判断是否为威胁：有4个己方棋子和1个空位
        if (pieceCount == THREAT_LENGTH - 1 && emptyCount >= 1) {
            // 返回第一个空位作为防守点
            if (!emptySpots.isEmpty()) {
                int[] defendSpot = emptySpots.get(0);
                return new Threat(defendSpot[0], defendSpot[1], dr, dc);
            }
        }

        return null;
    }

    /**
     * 创建防守单个威胁的走法
     */
    private Move createBlockingMove(Threat threat) {
        // 直接堵住威胁点
        int blockRow = threat.row;
        int blockCol = threat.col;

        // 先检查威胁点本身是否为空
        if (board.get((char)('A' + blockCol), (char)('A' + blockRow)) == PieceColor.EMPTY) {
            // 在威胁点附近找另一个空位
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;

                    int r2 = blockRow + dr;
                    int c2 = blockCol + dc;

                    if (r2 >= 0 && r2 < BOARD_SIZE && c2 >= 0 && c2 < BOARD_SIZE) {
                        if (board.get((char)('A' + c2), (char)('A' + r2)) == PieceColor.EMPTY) {
                            Move move = createMove(blockRow, blockCol, r2, c2);
                            if (move != null) {
                                return move;
                            }
                        }
                    }
                }
            }
        }

        // 如果威胁点不空或周围没空位，在威胁方向附近找空位
        for (int distance = 1; distance <= 3; distance++) {
            int r = threat.row + threat.dr * distance;
            int c = threat.col + threat.dc * distance;

            if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE) {
                if (board.get((char)('A' + c), (char)('A' + r)) == PieceColor.EMPTY) {
                    // 找一个附近的空位
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;

                            int r2 = r + dr;
                            int c2 = c + dc;

                            if (r2 >= 0 && r2 < BOARD_SIZE && c2 >= 0 && c2 < BOARD_SIZE) {
                                if (board.get((char)('A' + c2), (char)('A' + r2)) == PieceColor.EMPTY) {
                                    Move move = createMove(r, c, r2, c2);
                                    if (move != null) {
                                        return move;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 创建同时防守两个威胁的走法
     */
    private Move createDoubleDefenseMove(List<Threat> threats) {
        if (threats.size() != 2) return null;

        Threat t1 = threats.get(0);
        Threat t2 = threats.get(1);

        // 尝试直接用两个棋子分别堵住两个威胁点
        if (board.get((char)('A' + t1.col), (char)('A' + t1.row)) == PieceColor.EMPTY &&
                board.get((char)('A' + t2.col), (char)('A' + t2.row)) == PieceColor.EMPTY) {
            Move move = createMove(t1.row, t1.col, t2.row, t2.col);
            if (move != null) {
                return move;
            }
        }

        // 如果不行，尝试在威胁点附近选择
        List<int[]> candidateSpots = new ArrayList<>();

        // 添加威胁点本身（如果是空的）
        if (board.get((char)('A' + t1.col), (char)('A' + t1.row)) == PieceColor.EMPTY) {
            candidateSpots.add(new int[]{t1.row, t1.col});
        }
        if (board.get((char)('A' + t2.col), (char)('A' + t2.row)) == PieceColor.EMPTY) {
            candidateSpots.add(new int[]{t2.row, t2.col});
        }

        // 添加威胁点周围的空位
        addNearbyEmptySpots(t1.row, t1.col, candidateSpots);
        addNearbyEmptySpots(t2.row, t2.col, candidateSpots);

        // 尝试所有组合
        for (int i = 0; i < candidateSpots.size(); i++) {
            for (int j = i + 1; j < candidateSpots.size(); j++) {
                int[] spot1 = candidateSpots.get(i);
                int[] spot2 = candidateSpots.get(j);

                // 检查是否是不同的点
                if (spot1[0] == spot2[0] && spot1[1] == spot2[1]) continue;

                Move candidate = createMove(spot1[0], spot1[1], spot2[0], spot2[1]);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * 添加位置周围的空位到列表
     */
    private void addNearbyEmptySpots(int row, int col, List<int[]> spotList) {
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr == 0 && dc == 0) continue;

                int r = row + dr;
                int c = col + dc;

                if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE) {
                    if (board.get((char)('A' + c), (char)('A' + r)) == PieceColor.EMPTY) {
                        boolean exists = false;
                        for (int[] existing : spotList) {
                            if (existing[0] == r && existing[1] == c) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            spotList.add(new int[]{r, c});
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取所有空位
     */
    private List<int[]> getAllEmptySpots() {
        List<int[]> emptySpots = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board.get((char)('A' + c), (char)('A' + r)) == PieceColor.EMPTY) {
                    emptySpots.add(new int[]{r, c});
                }
            }
        }
        return emptySpots;
    }

    /**
     * 计算位置周围己方棋子数
     */
    private int countNearbyFriends(int row, int col) {
        int count = 0;
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr == 0 && dc == 0) continue;

                int r = row + dr;
                int c = col + dc;

                if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE) {
                    if (board.get((char)('A' + c), (char)('A' + r)) == _myColor) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 计算位置周围对方棋子数
     */
    private int countNearbyOpponents(int row, int col) {
        int count = 0;
        PieceColor opponent = (_myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;

        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr == 0 && dc == 0) continue;

                int r = row + dr;
                int c = col + dc;

                if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE) {
                    if (board.get((char)('A' + c), (char)('A' + r)) == opponent) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 选择最佳走法
     */
    private Move selectBestMove(List<Move> moves) {
        if (moves.isEmpty()) return null;

        Move bestMove = moves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (Move move : moves) {
            int score = evaluateMove(move);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    /**
     * 评估走法的分数（不修改实际棋盘）
     */
    private int evaluateMove(Move move) {
        int score = 0;

        // 1. 位置价值
        score -= Math.abs(move.col0() - CENTER) + Math.abs(move.row0() - CENTER);
        score -= Math.abs(move.col1() - CENTER) + Math.abs(move.row1() - CENTER);

        // 2. 棋子间距（适中为佳）
        int distance = Math.abs(move.col0() - move.col1()) +
                Math.abs(move.row0() - move.row1());
        if (distance >= 1 && distance <= 4) {
            score += 20;
        }

        // 3. 靠近己方棋子
        int row0 = move.row0() - 'A';
        int col0 = move.col0() - 'A';
        int row1 = move.row1() - 'A';
        int col1 = move.col1() - 'A';

        score += countNearbyFriends(row0, col0) * 2;
        score += countNearbyFriends(row1, col1) * 2;

        // 4. 形成棋型的潜力
        score += evaluateFormationPotential(move);

        return score;
    }

    /**
     * 评估走法形成棋型的潜力
     */
    private int evaluateFormationPotential(Move move) {
        int score = 0;

        // 检查每个落子点
        int row0 = move.row0() - 'A';
        int col0 = move.col0() - 'A';
        int row1 = move.row1() - 'A';
        int col1 = move.col1() - 'A';

        int[][] spots = {{row0, col0}, {row1, col1}};

        for (int[] spot : spots) {
            int r = spot[0];
            int c = spot[1];

            // 检查4个方向
            for (int[] dir : DIRECTIONS) {
                // 检查正向
                int forwardCount = countConsecutiveInDirection(r, c, dir[0], dir[1], _myColor);
                // 检查反向
                int backwardCount = countConsecutiveInDirection(r, c, -dir[0], -dir[1], _myColor);

                int total = forwardCount + backwardCount - 1; // 减去重复计算的当前点
                if (total >= 2) {
                    score += total * 8; // 每多一个连续棋子加8分
                }
            }
        }

        return score;
    }

    /**
     * 计算指定方向上的连续棋子数（不包含当前点）
     */
    private int countConsecutiveInDirection(int startRow, int startCol, int dr, int dc, PieceColor color) {
        int count = 0;
        int r = startRow + dr;
        int c = startCol + dc;

        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE &&
                board.get((char)('A' + c), (char)('A' + r)) == color) {
            count++;
            r += dr;
            c += dc;
        }

        return count;
    }

    /**
     * 创建走法
     */
    private Move createMove(int r1, int c1, int r2, int c2) {
        try {
            return new Move(
                    (char)('A' + c1), (char)('A' + r1),
                    (char)('A' + c2), (char)('A' + r2)
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建默认安全走法
     */
    private Move createSafeDefaultMove() {
        try {
            // 尝试中心区域
            if (board.get('J', 'J') == PieceColor.EMPTY && board.get('I', 'I') == PieceColor.EMPTY) {
                return new Move('J', 'J', 'I', 'I');
            }
            // 如果中心区域已满，找其他位置
            return createGuaranteedMove();
        } catch (Exception e) {
            return createGuaranteedMove();
        }
    }

    /**
     * 创建保障走法（总能找到合法走法）
     */
    private Move createGuaranteedMove() {
        List<int[]> emptySpots = getAllEmptySpots();

        if (emptySpots.size() >= 2) {
            // 使用前两个空位
            for (int i = 0; i < emptySpots.size(); i++) {
                for (int j = i + 1; j < emptySpots.size(); j++) {
                    int[] spot1 = emptySpots.get(i);
                    int[] spot2 = emptySpots.get(j);
                    Move move = createMove(spot1[0], spot1[1], spot2[0], spot2[1]);
                    if (move != null && board.legalMove(move)) {
                        return move;
                    }
                }
            }
        }

        // 如果还是找不到，使用超级简单的走法
        try {
            return new Move('A', 'A', 'A', 'B');
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 威胁类，用于存储威胁信息
     */
    private static class Threat {
        int row, col; // 需要防守的位置
        int dr, dc;   // 威胁方向

        Threat(int row, int col, int dr, int dc) {
            this.row = row;
            this.col = col;
            this.dr = dr;
            this.dc = dc;
        }
    }

    /**
     * 带分数的位置类
     */
    private static class ScoredSpot {
        int row, col, score;

        ScoredSpot(int row, int col, int score) {
            this.row = row;
            this.col = col;
            this.score = score;
        }
    }
}