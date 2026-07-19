# Media controls

The media controls screen is essential to help users control the media that
they're listening to. Create media controls using a 5-button layout to verify
that minimum tap targets are met. On larger screens that are 225 dp or larger,
you can add value by including a shortcut button for an important action such as
playback queue.

![](./media-controls-hero.png)

## Layout sections

The media controls screen is a fixed height screen. It's made up of three
vertical sections that have different responsive layouts and behavior.

![](./media-layout.png)

1. **Top section:** Contains media details
2. **Middle section:** Media controls
3. **Bottom section:** Configurable secondary buttons

## Considerations for larger screen

The large screen adaptations for media are solely focused on the media controls
screen. All other elements are captured in chip, button, and dialog items or in
the lists breakdown of large screen behavior.

### Buttons

You can add value by including a shortcut button for an important action, such
as playback queue. Using the overflow menu verifies that the additional
functionality is still consistent and accessible no matter what's the screen
size. The bottom section gains an additional slot for a button after the 225 dp
breakpoint making the maximum number of buttons 2 on smaller screens and 3 on
larger screens.

![](./media-buttons.png)

### Main control

The main control is usually a play and pause button. After the 225 dp
breakpoint, the control scales from 64dp to 80dp, which increases tap targets
for all controls within it.

![](./media-main-control.png)
Smaller screen (smaller than 225 dp)

<br />

- **Progress ring** = 64 x 64 dp / 3 dp stroke
- **Button** = 54 x 54 dp / 26 icon size
  Larger screen (225 dp and larger)

<br />

- **Progress ring** = 80 x 80 dp / 4 dp stroke
- **Button** = 70 x 70 dp / 32 icon size

<br />

## Button configuration

To follow touch target size principles, show only the 2-button layout before the
225 dp breakpoint and 3-button layouts past it.

The following examples show different button configurations:
![](./media-1-button-small.png)

<br />

1 Button Layout with overflow, smaller screen (192 dp)

![](./media-1-button-large.png)

<br />

1 Button Layout with overflow, larger screen (225 dp)

<br />

![](./media-2-button-small.png)

<br />

2 Button Layout with overflow, smaller screen (192 dp)

![](./media-2-button-large.png)

<br />

2 Button Layout with overflow, larger screen (225 dp)

<br />

![](./media-2-button-max-small.png)

<br />

2 Button (max) Layout with overflow, smaller screen (192 dp)

![](./media-3-button-large.png)

<br />

3 Button Layout with overflow, larger screen (225 dp)

<br />

## Marquee behavior

The header has a number of updates to improve readability and general
experience:

- A top margin of 12% on smaller screens and 13.2% on larger screens.
- Side margins for the song title of 17.6%
- Artist title margin of 12.5% on smaller screens and 14.5% on larger screens.
- Artist title truncates, while 8-dp gradients are used for scrolling song titles. If an icon is present, an additional 8 dp gap separates the song title from the icon. (The icon doesn't scroll with the song title.)

### With an icon

![](./media-non-scrolling-small.png)

<br />

Non-scrolling (short) song title on smaller screens (192 dp)

![](./media-non-scrolling-large.png)

<br />

Non-scrolling (short) song title on larger screens (225 dp)

<br />

![](./media-scrolling-small.png)

<br />

Scrolling (long) song title (left-aligned) on smaller screens (192 dp)

![](./media-scrolling-large.png)

<br />

Scrolling (long) song title (left-aligned) on larger screens (225 dp)

<br />

![](./media-scrolling-centered-small.png)

<br />

Scrolling (long) song title (center-aligned) on smaller screens (192 dp)

![](./media-scrolling-centered-large.png)

<br />

Scrolling (long) song title (center-aligned) on larger screens (225 dp)

<br />

### Without an icon

![](./media-non-scrolling-small-without-icon.png)

<br />

Non-scrolling (short) song title on smaller screens (192 dp)

![](./media-non-scrolling-large-without-icon.png)

<br />

Non-scrolling (short) song title on larger screens (225 dp)

<br />

![](./media-scrolling-small-without-icon.png)

<br />

Left aligned / non-scrolling (long) song title on smaller screens (192 dp)

![](./media-scrolling-large-without-icon.png)

<br />

Left aligned / non-scrolling (long) song title on larger screens (225 dp)

<br />

![](./media-scrolling-centered-small-without-icon.png)

<br />

Scrolling (long) song title (center-aligned) on smaller screens (192 dp)

![](./media-scrolling-centered-large-without-icon.png)

<br />

Scrolling (long) song title (center-aligned) on larger screens (225 dp)

<br />

## Tap targets

Main controls and bottom buttons use the available space to maximize the tap
target. The minimum tap target size is 48 x 48 dp on Wear OS devices, with
icons and buttons aligned to the center of the tap target.

### Main controls

![](./media-main-controls-small.png)

<br />

Small screen (192dp):

- **Main control** = 64 x 64 dp
- **Side controls** = 64 x 64 dp
  ![](./media-main-controls-large.png)

Large screen (225dp):

- **Main control** = 80 x 80 dp
- **Side controls** = 72.5 x 80 dp

### Bottom controls

![](./media-bottom-controls-small.png)

<br />

Smaller screens (192 dp) with a maximum of 2 buttons:

**Both buttons** = 68 x 60 dp

![](./media-bottom-controls-large.png)

<br />

Larger screens (225 dp) with a maximum of 3 buttons:

**All buttons** = 58 x 72.5 dp

<br />
