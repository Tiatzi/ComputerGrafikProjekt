package gnakcg.engine;

import org.joml.Vector2d;
import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Encapsulates mouse input.
 * Based on the LWJGL Book.
 */
public class MouseInput {

    private final Vector2d previousPos;

    private final Vector2d currentPos;

    private final Vector2f displVec;

    private boolean leftButtonPressed = false;

    private boolean rightButtonPressed = false;

    public MouseInput() {
        previousPos = new Vector2d(-1, -1);
        currentPos = new Vector2d(0, 0);
        displVec = new Vector2f();
    }

    public void init(Window window) {
        glfwSetCursorPosCallback(window.getWindowHandle(), (windowHandle, xpos, ypos) -> {
            currentPos.x = xpos;
            currentPos.y = ypos;
        });
        glfwSetMouseButtonCallback(window.getWindowHandle(), (windowHandle, button, action, mode) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_1)
                    leftButtonPressed = true;
                if (button == GLFW_MOUSE_BUTTON_2)
                    rightButtonPressed = true;
            }
            if (action == GLFW_RELEASE) {
                if (button == GLFW_MOUSE_BUTTON_1)
                    leftButtonPressed = false;
                if (button == GLFW_MOUSE_BUTTON_2)
                    rightButtonPressed = false;
            }
        });
    }

    /**
     * Returns the display vector. The display vector is the vector of the points of the last and the new position.
     */
    public Vector2f getDisplVec() {
        return displVec;
    }

    public void input() {
        displVec.x = 0;
        displVec.y = 0;
        if (previousPos.x > 0 && previousPos.y > 0) {
            double deltax = currentPos.x - previousPos.x;
            double deltay = currentPos.y - previousPos.y;
            displVec.y = (float) deltax;
            displVec.x = (float) deltay;
        }
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
    }

    public boolean isLeftButtonPressed() {
        return leftButtonPressed;
    }

    public boolean isRightButtonPressed() {
        return rightButtonPressed;
    }
}
