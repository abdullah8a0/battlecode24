package crayEcon;

import battlecode.common.*;
import crayEcon.util.FastMath;

public strictfp class Duck extends RobotPlayer {

    private enum State {
        EXPLORE, DEF, ATT, RUNNER
    }

    private enum ROLE {
        SETUP, BUILDER, DEFENDER, ATTACKER, RUNNER
    }

    private static State state = State.DEF;

    public static final RobotController rc = RobotPlayer.rc;
    public static RobotInfo[] enemies = new RobotInfo[0];
    public static int numEnemies = 0;
    public static RobotInfo[] allies = new RobotInfo[0];
    public static int numAllies = 0;
    public static FlagInfo[] enemyFlags = new FlagInfo[0];
    public static Boolean hasFlag = false;

    public static void sense() throws GameActionException {
        // enemies = rc.senseNearbyRobots(-1, oppTeam);
        // allies = rc.senseNearbyRobots(-1, myTeam);
        enemyFlags = rc.senseNearbyFlags(-1, oppTeam);
        hasFlag = rc.hasFlag();

        for (RobotInfo robot: rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                numAllies++;
            } else {
                numEnemies++;
            }
        }
    }

    public static void micro() throws GameActionException {
        if (hasFlag) 
            return;

        MapLocation[] enemyLocs = new MapLocation[enemies.length];
        for (int i = enemies.length; --i >= 0;) {
            enemyLocs[i] = enemies[i].location;
        }
        MapLocation[] enemyFlagLocs = new MapLocation[enemyFlags.length];
        int j = 0;
        for (int i = enemyFlags.length; --i >= 0;) {
            if (!enemyFlags[i].isPickedUp())
                enemyFlagLocs[j++] = enemyFlags[i].getLocation();
        }

        if (j > 0 && FastMath.rand256() < 256) {
            MapLocation closestFlag = enemyFlagLocs[0];
            if (rc.canPickupFlag(closestFlag)) {
                rc.pickupFlag(closestFlag);
                hasFlag = true;
            } else {
                follow(closestFlag);
                System.out.println("following flag " + closestFlag);
            }
        } if (enemies.length > 0) {
            MapLocation closestEnemyLoc = getClosestLoc(enemyLocs);
            if (rc.canAttack(closestEnemyLoc)) {
                rc.attack(closestEnemyLoc);
            } else {
                follow(closestEnemyLoc);
            }
        } else {

            switch (state) {
                case EXPLORE:
                    break;
                case RUNNER:
                    break;
                case DEF:
                    incest();
                    break;
                case ATT:
                    incest();
                    break;
            }
        }

    }

    // healing and attacking our own units to level up
    public static void incest() throws GameActionException {
        for (RobotInfo ally : allies) {
            if (rc.canHeal(ally.location)) {
                rc.heal(ally.location);
                break;
            }
        }
    }
        

    public static void macro() throws GameActionException {
            Direction centerDir = rc.getLocation().directionTo(new MapLocation(mapWidth / 2, mapHeight / 2));
        switch (state) {
            case EXPLORE:
                tryMoveDir(centerDir);
                break;
            case DEF:
                if (FastMath.rand256() < 100)
                    follow(mySpawnLoc);
                else 
                    randomMove();
                break;
            case ATT:
                MapLocation opposite = new MapLocation(mapWidth - mySpawnLoc.x, mapHeight - mySpawnLoc.y);
                follow(opposite);
                break;
            case RUNNER:
                follow(mySpawnLoc);
                break;
        }
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        sense();
        micro();
        macro();
        drive_state();
    }

    public static void drive_state() throws GameActionException {
        indicator += "state: " + state;
        switch (state) {
            case DEF:
                if (rc.getRoundNum() == 200) {
                    int rand = FastMath.rand256();
                    if (rand < 16) {
                        state = State.EXPLORE;
                    } else if (rand < 200) {
                        state = State.DEF;
                    } else {
                        state = State.ATT;
                    }
                }
                break;
            case EXPLORE:
            case ATT:
                if (hasFlag) 
                    state = State.RUNNER;
                break;
            case RUNNER:
                if (!hasFlag) {
                    int rand = FastMath.rand256();
                    if (rand < 16) {
                        state = State.EXPLORE;
                    } else if (rand < 200) {
                        state = State.DEF;
                    } else {
                        state = State.ATT;
                    }
                }
                break;
        }
    }

    public static final int[][] BFS25 = {
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {0, 2}, {-2, 0}, {0, -2},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
            {2, 2}, {2, -2}, {-2, 2}, {-2, -2}
    };

    static void randomMove() throws GameActionException {
        int starting_i = FastMath.rand256() % Constants.directions.length;
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = Constants.directions[i % 8];
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    static void tryMoveDir(Direction dir) throws GameActionException {
        if (rc.isMovementReady() && dir != Direction.CENTER) {
            if (rc.canMove(dir) && canPass(dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight()) && canPass(dir.rotateRight(), dir)) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft()) && canPass(dir.rotateLeft(), dir)) {
                rc.move(dir.rotateLeft());
            } else {
                randomMove();
            }
        }
    }
    static void follow(MapLocation location) throws GameActionException {
        tryMoveDir(rc.getLocation().directionTo(location));
    }

    static int getClosestID(MapLocation fromLocation, MapLocation[] locations) {
        int dis = Integer.MAX_VALUE;
        int rv = -1;
        for (int i = locations.length; --i >= 0;) {
            MapLocation location = locations[i];
            if (location != null) {
                int newDis = fromLocation.distanceSquaredTo(location);
                if (newDis < dis) {
                    rv = i;
                    dis = newDis;
                }
            }
        }
        assert dis != Integer.MAX_VALUE;
        return rv;
    }
    static int getClosestID(MapLocation[] locations) {
        return getClosestID(rc.getLocation(), locations);
    }

    static int getClosestDis(MapLocation fromLocation, MapLocation[] locations) {
        int id = getClosestID(fromLocation, locations);
        return fromLocation.distanceSquaredTo(locations[id]);
    }
    static int getClosestDis(MapLocation[] locations) {
        return getClosestDis(rc.getLocation(), locations);
    }

    static MapLocation getClosestLoc(MapLocation fromLocation, MapLocation[] locations) {
        return locations[getClosestID(fromLocation, locations)];
    }

    static MapLocation getClosestLoc(MapLocation[] locations) {
        return getClosestLoc(rc.getLocation(), locations);
    }


    static void moveToward(MapLocation location) throws GameActionException {
    }

    static int getSteps(MapLocation a, MapLocation b) {
        int xdif = a.x - b.x;
        int ydif = a.y - b.y;
        if (xdif < 0) xdif = -xdif;
        if (ydif < 0) ydif = -ydif;
        if (xdif > ydif) return xdif;
        else return ydif;
    }

    static int getCenterDir(Direction dir) throws GameActionException {
        double a = rc.getLocation().x - mapWidth/2.0;
        double b = rc.getLocation().y - mapHeight/2.0;
        double c = dir.dx;
        double d = dir.dy;
        if (a * d - b * c > 0) return 1;
        return 0;
    }

    private static final int BYTECODE_CUTOFF = 3000;
    static int getTurnDir(Direction direction, MapLocation target) throws GameActionException{
        return 1;    
    }

    static boolean canPass(MapLocation loc, Direction targetDir) throws GameActionException {
        if (loc.equals(rc.getLocation())) return true;
        if (!MapRecorder.check(loc, targetDir)) return false;
        if (!rc.canSenseLocation(loc)) return true;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot == null)
            return true;
        return false;
//        return FastMath.rand256() % 4 == 0; // rng doesn't seem to help
    }

    static boolean canPass(Direction dir, Direction targetDir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        if (!MapRecorder.check(loc, targetDir)) return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        // anchoring carriers don't yield to other robots
        if (robot == null)
            return true;
        return FastMath.rand256() % 4 == 0; // Does rng help here? Each rng is 10 bytecode btw
    }

    static boolean canPass(Direction dir) throws GameActionException {
        return canPass(dir, dir);
    }

    static Direction Dxy2dir(int dx, int dy) {
        if (dx == 0 && dy == 0) return Direction.CENTER;
        if (dx == 0 && dy == 1) return Direction.NORTH;
        if (dx == 0 && dy == -1) return Direction.SOUTH;
        if (dx == 1 && dy == 0) return Direction.EAST;
        if (dx == 1 && dy == 1) return Direction.NORTHEAST;
        if (dx == 1 && dy == -1) return Direction.SOUTHEAST;
        if (dx == -1 && dy == 0) return Direction.WEST;
        if (dx == -1 && dy == 1) return Direction.NORTHWEST;
        if (dx == -1 && dy == -1) return Direction.SOUTHWEST;
        assert false; // shouldn't reach here
        return null;
    }

}
