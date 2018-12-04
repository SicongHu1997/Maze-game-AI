package mycontroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import controller.CarController;
import tiles.HealthTrap;
import tiles.LavaTrap;
import tiles.MapTile;
import tiles.MudTrap;
import utilities.Coordinate;
import world.Car;
import world.WorldSpatial;

public class MyAIController extends CarController {
	
	private HashMap<Coordinate, MapTile> map = getMap();
	private ArrayList<Coordinate> unscouted = new ArrayList<>();
	private ArrayList<Coordinate> blacklist = new ArrayList<>();
	private static enum AccelState { FRONT, BACK, STOP };
	private AccelState accel = AccelState.STOP;
	// Car Speed to move at
	private final int CAR_MAX_SPEED = 1;
	private Coordinate target = null;
	private boolean foundHP = false;
	private int stage = 0;
	private Coordinate currentPosition = new Coordinate(getPosition());
	
	public MyAIController(Car car) {
		super(car);
		for (Coordinate entry : map.keySet()) {
			unscouted.add(entry);
		}
	}
	
	public class MyCoordinate extends Coordinate{
	    MyCoordinate parent;

	    public MyCoordinate(int x, int y) {
	        super(x,y);
	        this.parent = null;
	    }

	    public MyCoordinate(int x, int y, MyCoordinate parent) {
	        super(x,y);
	        this.parent = parent;
	    }

	    MyCoordinate getParent() {
	        return parent;
	    }
	}
	
	@Override
	public void update() {
		HashMap<Coordinate,MapTile> view = getView();
		for (Coordinate entry : view.keySet()) {
			if(!view.get(entry).isType(MapTile.Type.EMPTY)) {
				if(unscouted.contains(entry)) {
					unscouted.remove(entry);
				}
				map.put(entry, view.get(entry));
			}
		}
		
		currentPosition = new Coordinate(getPosition());
		
		for (Coordinate entry : map.keySet()) {
			if(map.get(entry) instanceof HealthTrap) {
				foundHP = true;
			}
		}
		// when we reach a target
		if(target!=null) {
			if((currentPosition.x==target.x)&&(currentPosition.y==target.y)){
				target = null;
				stage = 0;
			}
		}
		// when we step on a health trap
		if((getHealth()!=100)&&(map.get(currentPosition) instanceof HealthTrap)){
			applyBrake();
			return;
		}
		// if hp larger than 60 and there's key scouted, go find the key
		if((stage==0)&&(getHealth()>=60)){
			for (Coordinate entry : map.keySet()) {
				if(map.get(entry) instanceof LavaTrap) {
					if((((LavaTrap) map.get(entry)).getKey()!=0)&&(!(getKeys().contains(((LavaTrap) map.get(entry)).getKey())))) {
						if(!blacklist.contains(entry)) {
							stage = 1;
							target = null;
						}
					}
				}
			}
		}
		// if hp<=60 go to health trap
		if(getHealth()<=60) {
			stage = 2;
			target = null;
		}
		// if all key collected, go find exit
		if((stage==0)&&(hasAllKeys())) {
			stage = 3;
			target = null;
		}
		
		// stage0, scouting, choose a random unscouted position as target
		if((stage==0)&&(target==null)&&(!unscouted.isEmpty())) {
			target = unscouted.get((int)(Math.random()*unscouted.size()));
		}
		// stage1, go find the nearest key
		else if((stage==1)&&(target==null)) {
			Coordinate closeTarget = null;
			int close = 999;
			for (Coordinate entry : map.keySet()) {
				if(map.get(entry) instanceof LavaTrap) {
					if((((LavaTrap) map.get(entry)).getKey()!=0)&&(!(getKeys().contains(((LavaTrap) map.get(entry)).getKey())))) {
						if(Math.abs(entry.x-currentPosition.x)+Math.abs(entry.y-currentPosition.y)<close){
							if(!blacklist.contains(entry)) {
							closeTarget = entry;
							close = Math.abs(entry.x-currentPosition.x)+Math.abs(entry.y-currentPosition.y);
							}
						}
					}
				}
			}
			target = closeTarget;
		}
		// stage2, go to health trap
		else if((stage==2)&&(target==null)) {
			Coordinate closeTarget = null;
			int close = 999;
			for (Coordinate entry : map.keySet()) {
				if(map.get(entry) instanceof HealthTrap) {
					if(Math.abs(entry.x-currentPosition.x)+Math.abs(entry.y-currentPosition.y)<close){
						closeTarget = entry;
						close = Math.abs(entry.x-currentPosition.x)+Math.abs(entry.y-currentPosition.y);
					}
				}
			}
			target = closeTarget;
		}
		// stage 3 go find the exit
		else if((stage==3)&&(target==null)) {
			Coordinate closeTarget = null;
			int close = 999;
			for (Coordinate entry : map.keySet()) {
				if(map.get(entry).isType(MapTile.Type.FINISH)) {
					if(Math.abs(entry.x-currentPosition.x)+Math.abs(entry.y-currentPosition.y)<close){
						closeTarget = entry;
						close = Math.abs(entry.x-currentPosition.x)+Math.abs(entry.y-currentPosition.y);
					}
				}
			}
			target = closeTarget;
		}
		// choose the route which avoid lava if possible
		if(target!=null) {
			List<MyCoordinate> solve = solveavoidlava();
			if(solve.isEmpty()) {
				solve = solve();
				if(solve.isEmpty()) {
					// if a key cant be reached, put it into black list
					if(stage==1) {
						blacklist.add(target);
						stage=0;
					}
					target=null;
					applyBrake();
				}
			}
			if(solve.size()>=2) {
				move(CoorToDir(currentPosition,new Coordinate(solve.get(solve.size()-2).x,solve.get(solve.size()-2).y)));
			}
		}
	}
	
	
	
	private void move(WorldSpatial.Direction next) {
		if (next == null) {
			applyBrake();
			return;
		}		
		WorldSpatial.Direction curr = getOrientation();
		if (curr == next) {
			// Front
			goForward();
		} else if (WorldSpatial.changeDirection(curr, WorldSpatial.RelativeDirection.LEFT) == next) {
			// Left
			goLeft();
		} else if (WorldSpatial.changeDirection(curr, WorldSpatial.RelativeDirection.RIGHT) == next) {
			// Right
			goRight();
		} else {
			// Back
			goBackward();
		}
	}

	/** Public Methods **/
	
	public void goForward() {
		applyBrake();
		applyForwardAcceleration();
		accel = AccelState.FRONT;
	}
	
	public void goBackward() {
		applyBrake();
		applyReverseAcceleration();
		accel = AccelState.BACK;
	}
	
	public void goLeft() {
		if (accel == AccelState.BACK) {
			// The car is going backward, turn right to go left
			turnRight();
			goForward();
		} else {
			turnRight();
			goBackward();
		}
	}
	
	public void goRight() {
		if (accel == AccelState.BACK) {
			// The car is going backward, turn left to go right
			turnLeft();
			goForward();
		} else {
			turnLeft();
			goBackward();
		}
	}
	
	private boolean hasAllKeys() {
		for (int i = 1; i <= numKeys(); i++) if (!getKeys().contains(i)) return false;
		return true;
	}
	
	private WorldSpatial.Direction CoorToDir(Coordinate c1, Coordinate c2) {
		if (c1 == null || c2 == null) return null;
		
		int x1 = c1.x, x2 = c2.x, y1 = c1.y, y2 = c2.y;
		int xDelta = x2 - x1, yDelta = y2 - y1;
		if (xDelta == 0 && yDelta == 1) {
			return WorldSpatial.Direction.NORTH;
		} 
		if (xDelta == 0 && yDelta == -1) {
			return WorldSpatial.Direction.SOUTH;
		}
		if (xDelta == -1 && yDelta == 0) {
			return WorldSpatial.Direction.WEST;
		}
		if (xDelta == 1 && yDelta == 0) {
			return WorldSpatial.Direction.EAST;
		}
		
		// Not adjacent coordinates
		return null;
	}
	
	// Return the coordinate, one step from current coordinate c, at direction d
	private Coordinate DirToCoor(Coordinate c, WorldSpatial.Direction d) {
		int x = c.x, y = c.y;
		Coordinate cNext;
		switch (d) {
		case NORTH:
			y++;
			break;
		case SOUTH:
			y--;
			break;
		case WEST:
			x--;
			break;
		case EAST:
			x++;
			break;
		}
		
		cNext = new Coordinate(x, y);
		return cNext;
	}
	
	private static final int[][] DIRECTIONS = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
	// breadth first search
	public List<MyCoordinate> solve() {
		boolean[][] visited = new boolean[mapWidth()][mapHeight()];
        LinkedList<MyCoordinate> nextToVisit = new LinkedList<>();
        MyCoordinate start = new MyCoordinate(currentPosition.x,currentPosition.y);
        nextToVisit.add(start);
        while (!nextToVisit.isEmpty()) {
            MyCoordinate cur = nextToVisit.remove();
            if (!(map.keySet().contains(new Coordinate(cur.x,cur.y)))
            		|| (visited[cur.x][cur.y])) {
                continue;
            }

            if (map.get(new Coordinate(cur.x,cur.y)).isType(MapTile.Type.WALL)) {
                continue;
            }
            
            if (map.get(new Coordinate(cur.x,cur.y)) instanceof MudTrap) {
                continue;
            }

            if ((cur.x==target.x)&&(cur.y==target.y)) {
                return backtrackPath(cur);
            }

            for (int[] direction : DIRECTIONS) {
                MyCoordinate coordinate = new MyCoordinate(cur.x + direction[0], cur.y + direction[1], cur);
                nextToVisit.add(coordinate);
                visited[cur.x][cur.y] = true;
            }
        }
        return Collections.emptyList();
    }
	
	// breadth first search but avoid lava
	public List<MyCoordinate> solveavoidlava() {
		boolean[][] visited = new boolean[mapWidth()][mapHeight()];
        LinkedList<MyCoordinate> nextToVisit = new LinkedList<>();
        MyCoordinate start = new MyCoordinate(currentPosition.x,currentPosition.y);
        nextToVisit.add(start);
        while (!nextToVisit.isEmpty()) {
            MyCoordinate cur = nextToVisit.remove();
            if (!(map.keySet().contains(new Coordinate(cur.x,cur.y)))
            		|| (visited[cur.x][cur.y])) {
                continue;
            }

            if (map.get(new Coordinate(cur.x,cur.y)).isType(MapTile.Type.WALL)) {
                continue;
            }
            
            if (map.get(new Coordinate(cur.x,cur.y)) instanceof LavaTrap) {
                continue;
            }
            
            if (map.get(new Coordinate(cur.x,cur.y)) instanceof MudTrap) {
                continue;
            }

            if ((cur.x==target.x)&&(cur.y==target.y)) {
                return backtrackPath(cur);
            }

            for (int[] direction : DIRECTIONS) {
                MyCoordinate coordinate = new MyCoordinate(cur.x + direction[0], cur.y + direction[1], cur);
                nextToVisit.add(coordinate);
                visited[cur.x][cur.y] = true;
            }
        }
        return Collections.emptyList();
    }
	private List<MyCoordinate> backtrackPath(MyCoordinate cur) {
        List<MyCoordinate> path = new ArrayList<>();
        MyCoordinate iter = cur;

        while (iter != null) {
            path.add(iter);
            iter = iter.parent;
        }
        return path;
    }
}
