# inversek

This program animates a little robot arm using inverse kinematic, namelic the cyclic coordinate descent algorithm. Many thanks to Ryan Juckett 
(http://www.ryanjuckett.com) who has some great explanations of IK algorithms on his website!

The demo uses Box2D for physics, libgdx for maths and plain Java AWT for rendering.

![](https://github.com/toby1984/inversek/blob/master/screenshot.png?raw=true "")

# Controls

- SPACE key - emergency stop :)
- 'c' key - close gripper
- 'o' key - open gripper
- '+' key - increase joint angle (needs to have a joint selected by left-clicking)
- '-' key - decrease joint angle (needs to have a joint selected by left-clicking)
- left-click on bones/joints displays detail/debug info about angles and positions etc.
- middle-click - drops a ball
- right-click - sets a destination to solve the IK (note that for additional fun I constrained the solver so that only positions where the gripper is more or less perpendicular to the ground plane are considered...otherwise it's to hard to pick up balls from the ground). 

# Building

    mvn package

This will create a self-executable JAR in target/inversek.jar

# Running

java -jar target/inversek.jar

# Known glitches

- when sizing the window so that the gripper can go off-screen, a ball held by the gripper will disappear (because I'm discarding all balls that go off-screen)
- validating the solution found by the inverse kinematic solver currently does not simulate the arm's motion and thus does not recognize when the arm passes through the ground
- sometimes very small join angle adjustments cause the joint rotation to go past the desired target angle and force the joint to do a full rotation
