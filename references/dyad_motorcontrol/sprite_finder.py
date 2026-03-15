import cv2
import numpy as np
import sys

# Configuration
sprite_path = r"G:\My Drive\nirsense_local\Android_Apps\dyad_motorcontrol\assets\user_sheet.png"

# Initial grid parameters (adjustable)
num_cols = 11
num_rows = 8
col_width_pixels = 48.5
row_height_pixels = 66

# Current selection
current_col = 0
current_row = 0

# Sprite descriptions for reference
sprite_descriptions = {
    (1, 0): "Standing idle",
    (0, 6): "Run frame 1",
    (1, 6): "Run frame 2",
    (2, 6): "Run frame 3",
    (3, 6): "Run frame 4",
    (4, 6): "Run frame 5",
    (5, 6): "Run frame 6",
    (5, 2): "Jumping",
    (6, 2): "Web shoot",
    (7, 2): "Web hook 1",
    (8, 2): "Web hook 2",
    (9, 2): "Web hook 3",
    (10, 2): "Falling",
}

def draw_grid_and_selection(img, col_w, row_h, sel_col, sel_row, offset_x=0, offset_y=0):
    """Draw grid lines and highlight the selected cell."""
    display = img.copy()
    height, width = img.shape[:2]
    
    # Draw vertical lines (columns)
    for i in range(num_cols + 1):
        x = int(offset_x + i * col_w)
        if 0 <= x < width:
            cv2.line(display, (x, 0), (x, height), (0, 255, 0), 1)
            if i < num_cols:
                cv2.putText(display, str(i), (x + 5, 15), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.4, (0, 255, 0), 1)
    
    # Draw horizontal lines (rows)
    for i in range(num_rows + 1):
        y = int(offset_y + i * row_h)
        if 0 <= y < height:
            cv2.line(display, (0, y), (width, y), (0, 255, 0), 1)
            if i < num_rows:
                cv2.putText(display, str(i), (5, y + 15), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.4, (0, 255, 0), 1)
    
    # Highlight selected cell
    x1 = int(offset_x + sel_col * col_w)
    y1 = int(offset_y + sel_row * row_h)
    x2 = int(offset_x + (sel_col + 1) * col_w)
    y2 = int(offset_y + (sel_row + 1) * row_h)
    
    # Draw thick border around selected cell
    cv2.rectangle(display, (x1, y1), (x2, y2), (0, 0, 255), 3)
    
    # Show info
    desc = sprite_descriptions.get((sel_col, sel_row), "")
    info_text = f"Col: {sel_col}, Row: {sel_row}"
    if desc:
        info_text += f" - {desc}"
    
    cv2.putText(display, info_text, (10, height - 10), 
               cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
    
    # Show controls
    controls = [
        "WASD: Move selection",
        "-/=: Adjust col width",
        "[/]: Adjust row height",
        "IJKL: Adjust grid offset",
        "R: Reset",
        "P: Print values",
        "Q: Quit"
    ]
    for i, ctrl in enumerate(controls):
        cv2.putText(display, ctrl, (width - 250, 20 + i * 20), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1)
    
    # Show current measurements
    cv2.putText(display, f"Col Width: {col_w:.1f}px", (10, 30), 
               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
    cv2.putText(display, f"Row Height: {row_h:.1f}px", (10, 50), 
               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
    cv2.putText(display, f"Offset X: {offset_x:.1f}px", (10, 70), 
               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
    cv2.putText(display, f"Offset Y: {offset_y:.1f}px", (10, 90), 
               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
    
    return display

def main():
    global current_col, current_row, col_width_pixels, row_height_pixels
    
    # Load sprite sheet
    img = cv2.imread(sprite_path)
    if img is None:
        print(f"Error: Could not load image from {sprite_path}")
        return
    
    print(f"Loaded sprite sheet: {img.shape[1]}x{img.shape[0]} pixels")
    print("\nControls:")
    print("  W/A/S/D - Move selection (up/left/down/right)")
    print("  -/= (minus/equals) - Adjust column width (narrower/wider)")
    print("  [/] (brackets) - Adjust row height (shorter/taller)")
    print("  I/J/K/L - Move grid offset (up/left/down/right)")
    print("  R - Reset to default values")
    print("  P - Print current values")
    print("  Q - Quit")
    print("\nLooking for these frames:")
    for (col, row), desc in sorted(sprite_descriptions.items()):
        print(f"  Col {col}, Row {row}: {desc}")
    
    offset_x = 0.0
    offset_y = 0.0
    
    cv2.namedWindow('Sprite Sheet Grid Finder', cv2.WINDOW_NORMAL)
    cv2.resizeWindow('Sprite Sheet Grid Finder', 1200, 800)
    
    while True:
        # Draw and display
        display = draw_grid_and_selection(img, col_width_pixels, row_height_pixels, 
                                         current_col, current_row, offset_x, offset_y)
        cv2.imshow('Sprite Sheet Grid Finder', display)
        
        key = cv2.waitKey(50) & 0xFF
        
        # Selection movement (WASD)
        if key == ord('w'):
            current_row = max(0, current_row - 1)
        elif key == ord('s'):
            current_row = min(num_rows - 1, current_row + 1)
        elif key == ord('a'):
            current_col = max(0, current_col - 1)
        elif key == ord('d'):
            current_col = min(num_cols - 1, current_col + 1)
        
        # Adjust column width (- / =)
        elif key == ord('-') or key == ord('_'):
            col_width_pixels = max(1.0, col_width_pixels - 0.5)
        elif key == ord('=') or key == ord('+'):
            col_width_pixels += 0.5
        
        # Adjust row height ([ / ])
        elif key == ord('[') or key == ord('{'):
            row_height_pixels = max(1.0, row_height_pixels - 0.5)
        elif key == ord(']') or key == ord('}'):
            row_height_pixels += 0.5
        
        # Adjust grid offset (IJKL)
        elif key == ord('i'):
            offset_y -= 1
        elif key == ord('k'):
            offset_y += 1
        elif key == ord('j'):
            offset_x -= 1
        elif key == ord('l'):
            offset_x += 1
        
        # Reset
        elif key == ord('r'):
            col_width_pixels = 48.5
            row_height_pixels = 66.0
            offset_x = 0.0
            offset_y = 0.0
            current_col = 0
            current_row = 0
            print("\nReset to defaults")
        
        # Print current values
        elif key == ord('p'):
            print("\n" + "="*50)
            print("CURRENT VALUES:")
            print(f"  Columns: {num_cols}")
            print(f"  Rows: {num_rows}")
            print(f"  Column Width: {col_width_pixels:.1f} pixels")
            print(f"  Row Height: {row_height_pixels:.1f} pixels")
            print(f"  Offset X: {offset_x:.1f} pixels")
            print(f"  Offset Y: {offset_y:.1f} pixels")
            print(f"  Selected: Col {current_col}, Row {current_row}")
            desc = sprite_descriptions.get((current_col, current_row), "")
            if desc:
                print(f"  Description: {desc}")
            print("="*50)
        
        # Quit
        elif key == ord('q') or key == 27:  # Q or ESC
            break
    
    cv2.destroyAllWindows()
    
    print("\n" + "="*50)
    print("FINAL VALUES:")
    print(f"  spriteWidth = {col_width_pixels:.1f}f")
    print(f"  spriteHeight = {row_height_pixels:.1f}f")
    print(f"  offsetX = {offset_x:.1f}f")
    print(f"  offsetY = {offset_y:.1f}f")
    print("="*50)

if __name__ == "__main__":
    main()
