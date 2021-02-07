import javafx.scene.layout.Pane;
import javafx.scene.transform.Translate;

abstract public class Zoomable extends Pane {
    private Translate position, oldPosition;
    private double maxScale, scaleFactor;

    /**
     * Zoomable Pane with "zoom"/scale and move function
     * @param scaleFactor zoom-value that equals to a "zoom"/scroll; 1.2 is the normal value. If higher, it will zoom in more fastly
     * @param maxScale value that describes how deep you can zoom in maximally
     * @param width width value of the Pane
     * @param height height value of the Pane
     */
    Zoomable(double scaleFactor, double maxScale, double width, double height) {
        this(scaleFactor, maxScale);

        this.setPrefWidth(width);
        this.setPrefHeight(height);
    }

    /**
     * Zoomable Pane with "zoom"/scale and move function
     * @param scaleFactor zoom-value that equals to a "zoom"/scroll; 1.2 is the normal value. If higher, it will zoom in more fastly
     * @param maxScale value that describes how deep you can zoom in maximally
     */
    Zoomable(double scaleFactor, double maxScale){
        this.position = new Translate();
        this.oldPosition = new Translate();

        setScaleFactor(scaleFactor);
        setMaxScale(maxScale);

        this.setOnScroll(scrollEvent -> {
            // prevents upper (further from the root element) event-handler to be executed;
            // to prevent undesired actions to be executed, that can afflict the "zooming"
            scrollEvent.consume();

            scale(scrollEvent.getDeltaY(), new Translate(scrollEvent.getX(), scrollEvent.getY()));
        });

        this.setOnMousePressed(mouseEvent -> {
            // sets the current mouse position as the old position, so that the movement with
            // MouseDragged can be completed
            oldPosition.setX(mouseEvent.getSceneX());
            oldPosition.setY(mouseEvent.getSceneY());
        });

        this.setOnMouseDragged(mouseEvent -> {
            // while dragging the view, the position object needs to be updated
            moveTo(oldPosition, new Translate(mouseEvent.getSceneX(), mouseEvent.getSceneY()));
        });
    }

    /**
     * Moves the view from a point of the pane to another
     * @param startPosition translate object of the current position
     * @param endPosition translate object of the desired position where the view should be moved to
     */
    public void moveTo(Translate startPosition, Translate endPosition){
        // deltaX and deltaY are the difference of the old position to the new position
        double deltaX = (endPosition.getX() - startPosition.getX()) / this.getScaleX();
        double deltaY = (endPosition.getY() - startPosition.getY()) / this.getScaleY();

        // method that gets called before the movement
        beforeMove();

        // Moves the "view" by the difference of the two positions
        position.setX(position.getX() + deltaX);
        position.setY(position.getY() + deltaY);
        position = getInBoundsTranslate(position, this.getScaleX());
        setTransform(position);

        // this variable needs to be updated, because if you want to move around
        // it gets called every little distance you move, therefore this function gets called
        // multiple times within the movement where the startPosition is never the same
        this.oldPosition = endPosition.clone();

        // gets called after the movement has been completed
        afterMove();
    }

    /**
     * Scales the view to a desired value
     * @param scaleDeltaY corresponds to the deltaY of the scrollEvent which indicates how many pixel you will scroll
     * @param scalePosition a translate object of where the view should be zoomed in
     */
    public void scale(double scaleDeltaY, Translate scalePosition){
        Translate newPosition = new Translate();
        double deltaXscaled, deltaYscaled;
        double localScaleFactor = (scaleDeltaY > 0) ? scaleFactor : 1 / scaleFactor;

        if (getScale() * localScaleFactor < maxScale) {
            // Scale could already be at 0.8 and if we multiply a number with a number lower then 1,
            // although we might want to zoom in, we zoom out, because the zoomfactor will be
            // 0.8 * 1.2 = 0,96; that is why we need to multiply with the reciprocal value
            // e.g. 0.8 * (1 / 1.2) = 0.67
            if (getScale() * localScaleFactor < 1) {
                localScaleFactor = 1 / getScale();
            }

            // abstract method gets called before the Pane gets scaled
            beforeZoom(localScaleFactor);

            // the new position gets calculated, regarding the fact, that when you zoom in
            // the scalePosition gets moved by the value calculated
            // e.g. Pane  = 500x500; scalePosition = 100;100; position = 250;250
            // deltaXscaled = (500 / 2 - 100 - 250) * 0.8 - (500 / 2 - 100 - 250)
            // the difference that has to be moved horizontally equals to 20
            // therefore after wanting the movement completed at 100; 100 and at the same time zoom out,
            // we need to move 20px upwards; the same needs to be done with the Y-coordinate
            deltaXscaled = (((this.getWidth() / 2) - scalePosition.getX() - position.getX()) * localScaleFactor - ((this.getWidth() / 2) - scalePosition.getX() - position.getX()));
            deltaYscaled = (((this.getHeight() / 2) - scalePosition.getY() - position.getY()) * localScaleFactor - ((this.getHeight() / 2) - scalePosition.getY() - position.getY()));
            newPosition.setX(position.getX() + deltaXscaled);
            newPosition.setY(position.getY() + deltaYscaled);

            // the Pane itself needs to be scaled as well
            double newScale = getScale() * localScaleFactor;
            this.setScaleX(newScale);
            this.setScaleY(newScale);

            // check if the new coordinates are out of bounds and set them as new position
            position = getInBoundsTranslate(newPosition, this.getScaleX());
            setTransform(position);

            // abstract method gets called after the Pane got scaled
            afterZoom(localScaleFactor);
        }
    }

    /**
     * This function checks, if the newPosition is out of bounds. If this is the case, the view
     * will be moved within the bounds, on the border-side.
     * If the translate object is not out of bounds, the newPosition object will be applied without changes.
     * @param newPosition new possible translate object, which can be out of bounds
     * @param scaleValue current value of how much the Pane is scaled
     * @return the position translate object within the bounds
     */
    private Translate getInBoundsTranslate(Translate newPosition, double scaleValue){
        double zoomedRelativeWidth = this.getWidth() * (1 / scaleValue);
        double zoomedRelativeHeight = this.getHeight() * (1 / scaleValue);
        Translate inBoundsPosition = new Translate();

        // if the position is out of the left border.
        // e.g. newPosition = 80;9 zoomedRelativeWidth = 666 width = 800
        // therefore the view of the newScaledPosition is 666 wide, which means
        // on both sides we need a space of 666/2 which can't be provided,
        // because 666/2 + 80 > 800/2, which means the "view" would get out of bounds.
        // Now instead of moving 80px to the left, we calculate how much left it can go
        // without having to go out of bounds: px to the left =
        // 800/2 - 666/2 = 76px which can be moved to the left without crossing the borders
        if (newPosition.getX() + zoomedRelativeWidth / 2 > this.getWidth() / 2){
            inBoundsPosition.setX(this.getWidth() / 2 - zoomedRelativeWidth / 2);
        } else inBoundsPosition.setX(newPosition.getX());
        // position is out of the right border.
        // this isn't a else if, because if you zoom out, there is the case that you zoom
        // out of the Pane, which can lead to the case, where you cross the border on the left & right
        if (newPosition.getX() - zoomedRelativeWidth / 2 < this.getWidth() / -2){
            inBoundsPosition.setX((this.getWidth() / 2 - zoomedRelativeWidth / 2) * -1);
        }

        // position is out of the upper border
        if (newPosition.getY() + zoomedRelativeHeight / 2 > this.getHeight() / 2){
            inBoundsPosition.setY(this.getHeight() / 2 - zoomedRelativeHeight / 2);
        } else inBoundsPosition.setY(newPosition.getY());
        // position is out of the lower border
        if (newPosition.getY() - zoomedRelativeHeight / 2 < this.getHeight() / -2){
            inBoundsPosition.setY((this.getHeight() / 2 - zoomedRelativeHeight / 2) * -1);
        }

        return inBoundsPosition;
    }

    /**
     * Updates the translate object for the Zoomable class.
     * This is because if the translate object gets replaced with a new translate object
     * by a simple reference, the new translate object is not bound to the Pane on which it should operate.
     * @param translate new translate object that will get bound to the Pane
     */
    private void setTransform(Translate translate){
        this.getTransforms().clear();
        this.getTransforms().add(translate);
    }

    /**
     * Gets called before the "zooming" starts
     * @param scaleFactor value of the increment/decrement of the zoom
     */
    abstract void beforeZoom(double scaleFactor);

    /**
     * Gets called after the "zooming" has been completed
     * @param scaleFactor value of the increment/decrement of the zoom
     */
    abstract void afterZoom(double scaleFactor);

    /**
     * Gets called before the movement begins
     */
    abstract void beforeMove();

    /**
     * Gets called after the movement has been completed
     */
    abstract void afterMove();

    /**
     * Returns the Pane, on which the zoom is applied, which essentially is this object
     * @return Pane
     */
    public Pane getPane() {return this;}

    /**
     * Returns the current position of the "view"/"zoom"
     * @return a translate object with the coordinates
     */
    public Translate getPosition() {return position;}

    /**
     * @return current scale-value of the Pane
     */
    public double getScale() {return this.getScaleX();}

    /**
     * @return the scale-factor, which is a value that describes how much you can zoom, on a single scroll
     */
    public double getScaleFactor() {return this.scaleFactor;}

    /**
     * @param scaleFactor sets the scale-factor, which is a value that describes how much you can zoom, on a single scroll
     */
    public void setScaleFactor(double scaleFactor){this.scaleFactor = scaleFactor;}

    /**
     * @return biggest possible scale-value
     */
    public double getMaxScale() {return this.maxScale;}

    /**
     * @param maxScale sets the biggest possible scale-value
     */
    public void setMaxScale(double maxScale){this.maxScale = maxScale;}
}