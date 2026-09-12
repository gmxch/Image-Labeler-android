# Image Labeler Android (Batch Processing Edition)

![Static Badge](https://img.shields.io/badge/License-MIT-7F52FF)
![Static Badge](https://img.shields.io/badge/Android_Studio-Kotlin-7F52FF?logo=android%20studio)
![Static Badge](https://img.shields.io/badge/Open_Source-7F52FF?logo=open%20access&logoColor=white)
![Static Badge](https://img.shields.io/badge/Min_SDK-24_(Android_7.0)-brightgreen)

"Image Labeler"  is an open-source Android application designed for **efficient, batch annotation** of images to train object detection AI models. It allows users to draw multiple bounding boxes, assign class names, and export annotations in both **PASCAL VOC (XML)** and **YOLO** formats directly to the selected folder.

---

## What's changed: Initial Version vs Latest Version

| Feature | Initial Version (v1) | Latest Version (v2 - Current) |
| :--- | :--- | :--- |
| **Workflow** | Single image selection from Gallery | **Folder-based Batch Processing** (Roboflow/LabelImg style) |
| **UI Engine** | Heavy 3rd-party `Android-Image-Cropper` | Lightweight, custom **Canvas-based `DrawingView`** |
| **Labeling** | 1 Bounding Box per session | **Multiple Bounding Boxes** per image |
| **Transparency** | Solid background (hard to see transparent PNGs) | **Checkerboard pattern** for accurate transparent image labeling |
| **Navigation** | Exit and re-select to label another image | **Prev/Next** buttons with **Auto-save state** (labels persist) |
| **File Handling**| Prone to duplicate files `(1).txt` | **Robust overwrite mechanism** (Sanitized names, no spam files) |
| **UX** | Manual keyboard handling | **Auto-focus keyboard** + **Enter/Done key** to instantly save |
| **Android 13+**| Permission issues | Fully compatible with Scoped Storage & `TIRAMISU` URI handling |

---

## Table of Contents
- [Demo](#demo)
- [Features](#features) 
- [Usage](#usage)
- [Tips](#tips)
- [Contributing](#contributing)
- [Feedback](#feedback)
- [License](#license)
- [Acknowledgements](#acknowledgements)
- [Libraries](#libraries)
  
## Demo
This app aims to simplify the process of creating labeled data for object detection by allowing users to easily annotate entire folders of images with bounding boxes and class names, without repetitive menu navigation.

<video src="https://raw.githubusercontent.com/gmxch/Image-Labeler-android/master/demo/screen-20260913-042259.mp4" controls width="100%" max-width="600px"></video>

<video src="https://raw.githubusercontent.com/gmxch/Image-Labeler-android/master/demo/screen-20260913-042334.mp4" controls width="100%" max-width="600px"></video>

## Features
-  **Batch Folder Processing**: Select a folder once, label all images inside sequentially.
-  **Custom Drawing Canvas**: Draw, edit, and **long-press to delete** multiple bounding boxes per image.
-  **Transparency Support**: Checkerboard background ensures accurate labeling of PNGs with alpha channels.
-  **Auto-State Persistence**: Labels are cached in memory when navigating (Prev/Next) and safely written to disk on save.
-  **Dual Export Formats**: Generates both **PASCAL VOC (.xml)** and **YOLO (.txt)** files side-by-side with the original images.
-  **Optimized UX**: Auto-opening keyboard and "Enter/Done" key support for lightning-fast class naming.
-  **Robust File Handling**: Prevents duplicate `(1).txt` files and safely handles special characters in filenames.
-  **Modern Android Support**: Fully compatible with Android 7.0 (API 24) up to Android 14 (API 34), including scoped storage permissions.

## Usage
1. **Clone & Build**: 
```bash
   git clone https://github.com/gmxch/Image-Labeler-android.git
   # Open in Android Studio or build via Gradle
```
2. **Select Folder**: Open the app and tap **"Pilih Folder Dataset"** to grant access to your image directory.
3. **Start Labeling**: Tap **"Mulai Labeling"**. The first image will load.
4. **Annotate**: 
   - Drag your finger to draw a bounding box.
   - A dialog will appear. Type the class name (e.g., `car`, `person`) and press **Enter/Done** on your keyboard.
   - To delete a box, **long-press** on it.
5. **Navigate**: Use **Prev** or **Next** to move between images. Your labels are automatically saved in the background.
6. **Export**: Tap **"Simpan Label"** to ensure all `.xml` and `.txt` files are written to your chosen folder.
7. **Train**: Use the generated files directly in YOLOv8, Detectron2, or any other object detection framework.

##  Pro Tips & Testing
- **Quick Testing (No Build Required):** Want to try the app immediately without compiling? Check the `apk/` folder in this repository. It contains ready-to-install APK files for both the **Initial Version (v1)** and the **Latest Version (v2)**.
- **Side-by-Side Comparison:** You can install both versions on the same device to directly compare the old single-image workflow with the new batch processing features.
- **Dataset Naming:** While the app now automatically sanitizes special characters, it's still a best practice to name your dataset images cleanly (e.g., `image_001.jpg` instead of `my image (copy).jpg`) before starting the labeling process.

## Contributing
Contributions are highly welcome! Here's how you can help:
-  Report bugs or edge cases (e.g., specific image formats).
-  Fix bugs and submit Pull Requests.
-  Suggest new features (e.g., class presets, YOLO class ID mapping).
-  Write and improve documentation or translations.

## Feedback
If you have any feedback, suggestions, or issues, please open an [Issue](https://github.com/gmxch/Image-Labeler-android/issues) or reach out via email at `gamamoch@gmail.com`.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgements
A huge thank you to the original creator of this project, **[Mahdi Ahmadnejad](https://github.com/MahdiAhmadnejad)**, for building the foundation of this Image Labeler app. This repository is a fork and major evolution of their original work, adapted to support modern, efficient batch processing workflows for object detection datasets. 

## Libraries
The latest version has been optimized to remove heavy dependencies. Current libraries in use:
- [Xerces2-J](https://github.com/apache/xerces2-j) (XML Parsing/Generation)
- [SDP & SSP](https://github.com/intuit/sdp) (Scalable Size Units for responsive UI)

*(Removed in v2: `Android-Image-Cropper` and `Picasso`, replaced by native Android `Canvas` and `BitmapFactory` for better performance and smaller APK size).*