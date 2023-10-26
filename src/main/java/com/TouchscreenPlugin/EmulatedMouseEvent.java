package com.TouchscreenPlugin;

import org.apache.commons.lang3.tuple.Pair;

import java.awt.event.MouseEvent;

public class EmulatedMouseEvent extends Pair<Integer, MouseEvent> {

    private Integer    eventID;
    private MouseEvent eventPrototype;

    public EmulatedMouseEvent(Integer eventID, MouseEvent event) {
        super();
        this.eventID = eventID;
        this.eventPrototype = event;
    }

    @Override
    public Integer getLeft() {
        return eventID;
    }

    @Override
    public MouseEvent getRight() {
        return eventPrototype;
    }

    @Override
    public MouseEvent setValue(MouseEvent value) {
        MouseEvent prev = eventPrototype;
        eventPrototype = value;
        return prev;
    }
}
