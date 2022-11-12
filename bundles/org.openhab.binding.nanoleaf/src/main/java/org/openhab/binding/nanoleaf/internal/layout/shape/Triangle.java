/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.nanoleaf.internal.layout.shape;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.nanoleaf.internal.layout.Point2D;
import org.openhab.binding.nanoleaf.internal.layout.ShapeType;

/**
 * A triangular shape.
 *
 * @author Jørgen Austvik - Initial contribution
 */
@NonNullByDefault
public class Triangle extends Shape {
    public Triangle(ShapeType shapeType, int panelId, Point2D position, int orientation) {
        super(shapeType, panelId, position, orientation);
    }

    @Override
    public List<Point2D> generateOutline() {
        int height = (int) (getShapeType().getSideLength() * Math.sqrt(3) / 2);
        Point2D v1;
        if (pointsUp()) {
            v1 = new Point2D(0, height * 2 / 3);
        } else {
            v1 = new Point2D(0, -height * 2 / 3);
        }

        Point2D v2 = v1.rotate((2.0 / 3.0) * Math.PI);
        Point2D v3 = v1.rotate((-2.0 / 3.0) * Math.PI);
        return Arrays.asList(v1.move(getPosition()), v2.move(getPosition()), v3.move(getPosition()));
    }

    @Override
    public Point2D labelPosition(Graphics2D graphics, List<Point2D> outline) {
        Point2D[] bounds = findBounds(outline);
        int midX = bounds[0].getX() + (bounds[1].getX() - bounds[0].getX()) / 2;
        int midY = bounds[0].getY() + (bounds[1].getY() - bounds[0].getY()) / 2;

        Rectangle2D rect = graphics.getFontMetrics().getStringBounds(Integer.toString(getPanelId()), graphics);
        return new Point2D(midX - (int) (rect.getWidth() / 2), midY - (int) (rect.getHeight() / 2));
    }

    private boolean pointsUp() {
        // Upward: even multiple of 60 degrees rotation
        return ((getOrientation() / 60) % 2) == 0;
    }
}
