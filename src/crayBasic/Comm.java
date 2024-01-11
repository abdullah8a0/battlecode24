package crayBasic;

import battlecode.common.*;
import crayBasic.util.FastIterableIntSet;

/***
 *
 * to represent coord, the value in the shared array is 1 more than usual
 * so that (0,0) in shared array means null
 *
 * Shared array
 * 0-47: 4*2 6bits int specifying the coord of friendly HQs
 * 48-50: 3 bit whether symmetries of the map have been eliminated:
 * [ROTATIONAL, VERTIAL, HORIZONTAL]
 * 64-67: 4 bits indicating whether the 4 HQs are congested
 *
 *
 * enemy report starting 224
 * 2 bits broadcast roundNumber for alive check
 * 12 bits location,
 */

public class Comm extends RobotPlayer {
    private static final int ARRAY_LENGTH = 64;
    private static final int SYM_BIT = 48;
    private static final int ENEMY_ARR_IDX = 14;
    private static final int ENEMY_BIT = ENEMY_ARR_IDX * 16;
    private static int numFound;

    private static int[] buffered_share_array = new int[ARRAY_LENGTH];
    private static FastIterableIntSet changedIndexes = new FastIterableIntSet(ARRAY_LENGTH);


    public static MapLocation[] friendlyFlagLocations = {null, null, null};
    public static MapLocation[] enemyFlagLocations = {null, null, null};
    public static MapLocation[] enemySpawnLocations = new MapLocation[9*3];

    public static void turn_starts() throws GameActionException {
        boolean needSymUpdate = false;
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (rc.readSharedArray(i) != buffered_share_array[i]) {
                if (i == 3 && !isSymmetryConfirmed) {
                    needSymUpdate = true;
                }
                buffered_share_array[i] = rc.readSharedArray(i);
            }
        }

        if (needSymUpdate || turnCount == 0) {
            updateSym();
        }

        updateFriendlyFlagLocations(); // TODO: maybe we can do this less frequently
    }

    public static void updateFriendlyFlagLocations() throws GameActionException {
        int flag0 = readBits(0, 12);
        int flag1 = readBits(12, 12);
        int flag2 = readBits(24, 12);
        friendlyFlagLocations[0] = int2loc(flag0);
        friendlyFlagLocations[1] = int2loc(flag1);
        friendlyFlagLocations[2] = int2loc(flag2);
        
    }

    // IMPORTANT: always ensure that any write op is performed when writable
    public static void commit_write() throws GameActionException {
        if (changedIndexes.size > 0) {
            changedIndexes.updateIterable();
            int[] indexes = changedIndexes.ints;
            for (int i = changedIndexes.size; --i>=0;) {
                if (rc.canWriteSharedArray(indexes[i], 0)) {
                    int before = rc.readSharedArray(indexes[i]);
                    rc.writeSharedArray(indexes[i], buffered_share_array[indexes[i]]);
                    changedIndexes.remove(indexes[i]);
                }
            }
        }
    }

    public static void print_debug() {
        System.out.println("Comm: ");
        System.out.println("    friendlyFlagLocations: ");
        int flag0 = readBits(0, 12);
        int flag1 = readBits(12, 12);
        int flag2 = readBits(24, 12);
        System.out.println("        " + int2loc(flag0));
        System.out.println("        " + int2loc(flag1));
        System.out.println("        " + int2loc(flag2));

    }

    public static int flagInit(MapLocation location) throws GameActionException {
        for (int i = 0; i < GameConstants.NUMBER_FLAGS; i++) {
            if (friendlyFlagLocations[i] == null) {
                friendlyFlagLocations[i] = location;
                writeBits(i * 12, 12, loc2int(location));
                numFound = i + 1;
                guessSym();
                commit_write();
                return i;
            } else if (friendlyFlagLocations[i].equals(location)) {
                return i;
            }
        }
        assert false;
        return -1;
    }

    /*
    private static void updateFlagLocations() throws GameActionException {
        numFound = 0;
        for (int i = 0; i < GameConstants.NUMBER_FLAGS; i++) {
            friendlyFlagLocations[i] = int2loc(readBits(12 * i, 12));
            if (friendlyFLocations[i] != null) {
                numFound++;
            } else {
                break;
            }
        }
        for (int i = numFound; --i >= 0;) {
            for (int sym = 3; --sym >= 0;) {
                if (isSymEliminated[sym])
                    continue;
                MapLocation loc = friendlySpawnLocations[i];
                MapLocation symLoc = new MapLocation(
                        (sym & 1) == 0? mapWidth - loc.x - 1 : loc.x,
                        (sym & 2) == 0? mapHeight - loc.y - 1 : loc.y);
                if (!rc.canSenseLocation(symLoc)) continue;
                RobotInfo robot = rc.senseRobotAtLocation(symLoc);
                if (robot == null || robot.team != oppTeam) {
                    isSymEliminated[sym] = true;
                    writeBits(SYM_BIT + sym, 1, 1);
                    System.out.printf("eliminate sym %d from Spawn at %s\n", sym, loc);
                }
            }
        }
        guessSym();
    }
    */


    // enemy report
    // encoding 2 bit of round + 12 bit of loc
    private static final int ENEMY_LEN = 10;

    // called by HQ 0 at start
    public static void updateExpiredEnemy() throws GameActionException {
        int curRound = rc.getRoundNum() % 4;
        int roundToClear = 0;
        switch (curRound) {
            case 3: roundToClear = 1; break;
            case 2: roundToClear = 0; break;
            case 1: roundToClear = 3; break;
            case 0: roundToClear = 2; break;
        }
        for (int i = ENEMY_LEN; --i >= 0;) {
            int val = buffered_share_array[ENEMY_ARR_IDX + i];
            if (val != 0 && (val >> 12) == roundToClear) {
                rc.writeSharedArray(ENEMY_ARR_IDX + i, 0);
                //                System.out.printf("clear %s", int2loc(val & 0xFFF));
            }
        }
    }

    public static MapLocation updateEnemy() throws GameActionException {
        MapLocation rv = null;
        int bestDis = Integer.MAX_VALUE;
        int writeLoc = 0;
        boolean canWrite = rc.canWriteSharedArray(0, 0);
        for (int i = ENEMY_LEN; --i >= 0;) {
            int val = buffered_share_array[ENEMY_ARR_IDX + i];
            if (val != 0) {
                MapLocation loc = int2loc(val & 0xFFF);
                if (rc.canSenseLocation(loc)) {
                    RobotInfo robot = rc.senseRobotAtLocation(loc);
                    if (canWrite) {
                        // we always erase what we can see, since we will also report them
                        rc.writeSharedArray(ENEMY_ARR_IDX + i, 0);
                    }
                    if (robot != null && robot.team != oppTeam) {
                        continue;
                    }
                }
                int dis = rc.getLocation().distanceSquaredTo(loc);
                if (dis < bestDis) {
                    bestDis = dis;
                    rv = loc;
                }
            } else {
                writeLoc = i;
            }
        }
        if (canWrite) {
            int curRound = rc.getRoundNum() % 4;
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, oppTeam);
            for (int i = Math.min(5, enemies.length); --i >= 0;) {
                RobotInfo robot = enemies[i];
                rc.writeSharedArray(ENEMY_ARR_IDX + writeLoc, loc2int(robot.location) + (curRound << 12));
                writeLoc++;
                if (writeLoc == ENEMY_LEN) {
                    writeLoc = 0;
                }
            }
        }
        return bestDis <= 25? rv : null;
    }



    // symmetry checker
    // bit 0-2: whether sym is eliminated
    public static final int SYM_ROTATIONAL = 0;
    public static final int SYM_VERTIAL = 1;
    public static final int SYM_HORIZONTAL = 2;

    public static int symmetry;
    public static boolean isSymmetryConfirmed;
    public static boolean needSymmetryReport;
    public static boolean[] isSymEliminated = new boolean[3];

    public static void eliminateSym(int sym) throws GameActionException {
        isSymEliminated[sym] = true;
        if (rc.canWriteSharedArray(0, 0)) {
            writeBits(SYM_BIT + sym, 1, 1);
            commit_write();
        } else {
            needSymmetryReport = true;
        }
        guessSym();
    }

    public static void updateSym() {
        int bits = readBits(SYM_BIT, 3);
        needSymmetryReport = false;
        for (int sym = 3; --sym >= 0; ) {
            if (!isSymEliminated[sym] && (bits & (1 << (2 - sym))) > 0) {
                isSymEliminated[sym] = true;
            } else if (isSymEliminated[sym] && (bits & (1 << (2 - sym))) == 0) {
                needSymmetryReport = true;
            }
        }
        guessSym();
    }

    public static void reportSym() throws GameActionException {
        if (!needSymmetryReport)
            return;
        int bits = readBits(SYM_BIT, 3);
        for (int sym = 3; --sym >= 0; ) {
            if (isSymEliminated[sym] && (bits & (1 << (2 - sym))) == 0) {
                writeBits(SYM_BIT + sym, 1, 1);
            }
        }
        needSymmetryReport = false;
    }

    public static void guessSym() {
        int numPossible = 0;
        for (int sym = 3; --sym >=0; ) {
            if (!isSymEliminated[sym]) {
                numPossible++;
                symmetry = sym;
            }
        }
        if (numPossible == 0) {
            System.out.println("impossible that no sym is correct, guess rotation");
            symmetry = 0;
            numPossible = 1;
        }
        if (numPossible == 1) {
            isSymmetryConfirmed = true;
        } else {
            isSymmetryConfirmed = false;
        }
        // update enemy spawn zones
        for (int i = GameConstants.NUMBER_FLAGS; --i >= 0;) {
            MapLocation loc = allySpawnLocs[i];
            enemySpawnLocations[i] = new MapLocation(
                    (symmetry & 1) == 0? mapWidth - loc.x - 1 : loc.x,
                    (symmetry & 2) == 0? mapHeight - loc.y - 1 : loc.y);
        }
    }

    // helper funcs
    private static int readBits(int left, int length) {
        int endingBitIndex = left + length - 1;
        int rv = 0;
        while (left <= endingBitIndex) {
            int right = Math.min(left | 0xF, endingBitIndex);
            rv = (rv << (right - left + 1)) + ((buffered_share_array[left/16] % (1 << (16 - left%16))) >> (15 - right % 16));
            left = right + 1;
        }
        return rv;
    }

    private static void writeBits(int startingBitIndex, int length, int value) {
        assert value < (1 << length);
        int current_length = length;
        while (current_length > 0){
            int current_ending = startingBitIndex + current_length - 1;
            int len = Math.min(current_ending%16+1, current_length);
            int left = current_ending - len + 1;
            int original_value = (buffered_share_array[left/16] % (1 << (16 - left%16))) >> (15 - current_ending % 16);
            int new_value = value % (1 << len);
            value >>= len;
            if (new_value != original_value){
                changedIndexes.add(current_ending / 16);
                buffered_share_array[current_ending / 16] ^= (new_value^original_value) << (15 - current_ending % 16);
            }
            current_length -= len;
        }
    }

    private static MapLocation int2loc(int val) {
        if (val == 0) {
            return null;
        }
        return new MapLocation(val / 64 - 1, val % 64 - 1);
    }

    private static int loc2int(MapLocation loc) {
        if (loc == null)
            return 0;
        return ((loc.x + 1) * 64) + (loc.y + 1);
    }

    // sanity check func
    public static void test_bit_ops() throws GameActionException {
        writeBits(11, 10, 882);
        assert(readBits(11, 10) == 882);
        writeBits(8, 20, 99382);
        assert(readBits(8, 20) == 99382);
        writeBits(900, 10, 9);
        assert(readBits(900, 10) == 9);
        assert(readBits(8, 20) == 99382);
        writeBits(905, 10, 922);
        assert(readBits(905, 10) == 922);
    }
}
