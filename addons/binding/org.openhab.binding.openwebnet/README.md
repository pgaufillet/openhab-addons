# OpenWebNet Binding

This binding integrates BTicino / Legrand devices using OpenWebNet protocol over Zigbee aka Open/ZigBee and commercially MyHOME(r) Play
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD

=======
This binding integrates the [Philips Hue Lighting system](http://www.meethue.com).
>>>>>>> bd6e49eb3... 1st commit on openwebnet/zigbee addons
=======
>>>>>>> 5bc0c6d35... Fix in README.md
=======

>>>>>>> 9dfe1a9b5... Fix README.md
=======
This binding integrates the [Philips Hue Lighting system](http://www.meethue.com).
>>>>>>> e782afa70... 1st commit on openwebnet/zigbee addons
=======
>>>>>>> c542b86f8... Fix in README.md
The integration happens through a USB dongle, which acts as a gateway to the Open/ZigBee devices.

![BTI-3578/LEG088328](doc/LEG088328.jpg)

## Supported Things

The USB dongle is required as a "bridge" for accessing any other Open/ZigBee devices.

Almost all available Open/ZigBee devices are supported by this binding. 
Please note that the devices need to be attached to same Network as USB dongle before it is possible for this binding to use them.

The Hue binding supports mostly all lighting devices (switch and dimmer) and automation devices.

## Discovery

All discoveries can be done through PaperUI.

The discovery of the USB dongle is performed on user request by checking all serial interface on the computer.

Once it is added as a Thing, a second user request is needed to find the devices themselves. This step takes several tens of seconds.

## Binding Configuration

No configuration is needed.

## Thing Configuration

No Thing configuration is needed.

## Channels

All devices support some of the following channels:

| Channel Type ID   | Item Type       | Description                                                                                                                                   |
|-------------------|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| switch            | Switch          | This channel supports switching the device on and off.                                                                                        |   
| brightness        | Dimmer          | This channel supports adjusting the brightness value. Note that this is channel also compatible of switch channel.                            |
| shutter           | Rollershutter   | This channel supports activation of roller shutter (Up, down, stop), if the shutter travel is configured, the position can be set/returned.   |                                                                                          


## Full Example

<<<<<<< HEAD
None for now
=======
TODO
>>>>>>> e782afa70... 1st commit on openwebnet/zigbee addons
