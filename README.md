# ErgoTyper

Custom keyboard layouts built from how you actually type.

QWERTY was designed for typewriters, and alternatives like Dvorak and Colemak are designed for the "average" user. ErgoTyper measures your hands instead. An Android app tracks your hand positions with a phone camera while you type, then an optimizer uses that data to generate a layout that minimizes your finger travel.

ECE528 final project, Colorado State University. Noah Klein and Trent Nosky. Full write-up in [`docs/ECE528_Final_Project_Report.pdf`](docs/ECE528_Final_Project_Report.pdf).

## How it works

1. **Track.** You wear pink stickers on your index knuckles. A quantized YOLOv8 Nano model on the phone finds them in each frame.
2. **Log.** A USB keyboard is plugged into the phone via OTG. Each keypress is logged with the latest hand position, so every key gets a physical "cost" specific to you.
3. **Optimize.** The optimizer combines that cost map with bigram frequencies from WikiText-2 and searches for the layout with the lowest total travel (distance × frequency), using simulated annealing.

## Repo layout

```
android/MainActivity.kt              Camera + hand tracking + keypress logging (Kotlin, CameraX, TFLite)
training/yolo_tracking_training.py   Trains YOLOv8n on the sticker dataset and exports INT8 TFLite
optimizer/layout_generator.py        Builds the cost map from the log and evolves a layout
docs/                                Project report
```

## Usage

**1. Train the tracker** (we used Google Colab)

```bash
pip install ultralytics roboflow
export ROBOFLOW_API_KEY=your_key
python training/yolo_tracking_training.py
```

Copy the exported `*_int8.tflite` into the Android app's assets as `model.tflite`.

**2. Collect data.** Drop `MainActivity.kt` into an Android project (needs CameraX and the TFLite Task Vision library, plus a layout with a `PreviewView` named `viewFinder`). Mount the phone about 30cm above the keyboard, plug in the keyboard, and type. Then pull the log:

```bash
adb logcat -s ErgoTyper > ergo_log.txt
```

**3. Generate a layout**

```bash
pip install -r requirements.txt
python optimizer/layout_generator.py
```

Put `ergo_log.txt` in the working directory. It downloads the WikiText-2 corpus on first run. With no log it falls back to a generic grid.

## Results

Tested on a Samsung S25 FE.

| | |
|---|---|
| Tracker mAP@50 | 0.97 |
| FP32 inference | ~12 FPS |
| INT8 inference (NNAPI) | 30 FPS |
| Layout score, QWERTY | 100 |
| Layout score, random | 145 |
| Layout score, optimized | **58** (42% less finger travel) |

The optimized layout puts E, T, A, O, I, N where the hands rest and learns hand alternation on its own.

## Limitations

- Tracks index knuckles only, not individual fingers.
- Cost is distance only. It doesn't know your pinky is weaker than your index.
- 30 FPS is about 10 frames per keystroke, which is too coarse for precise finger flight times.
- Switching layouts is hard. Muscle memory wins at first.
