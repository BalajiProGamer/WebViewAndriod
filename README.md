# WebView Android App

## About  
This is a WebView-based Android application developed using Java in Android Studio. The app provides a seamless way to integrate web content within a native Android environment, allowing users to browse a specific website or web application within an embedded WebView. Additionally, it supports **Google Sign-In** via Chrome Custom Tabs for enhanced authentication security.

## Features  
- Displays web content within an Android WebView  
- Supports JavaScript for interactive web pages  
- Customizable WebView settings (e.g., Zoom, Cache, Cookies)  
- Handles external links and user navigation  
- **Google Sign-In Support:** Opens Google authentication in Chrome Custom Tabs and redirects back to WebView after login

## Installation  

1. Clone the repository:  
   ```sh
   git clone https://github.com/your-username/repository-name.git
   ```
2. Open the project in **Android Studio**  
3. Sync Gradle and build the project  
4. Run the app on an emulator or a physical Android device  

## Requirements  
- **Android Studio** (latest version recommended)  
- **Gradle Build Tool:** 8.1.0  
- **Android Gradle Plugin (AGP):** 8.8.1  
- **JDK:** 17  
- **Minimum SDK:** API 21 (Lollipop)  

## Usage  
Modify the `WebView` URL inside `MainActivity.java` to load your preferred website:  
```java
myWeb.loadUrl("https://erp.tetroninfotech.online/");
```

### Google Sign-In Support
To ensure Google Sign-In works properly within the app, the WebView will detect authentication attempts and open them in **Chrome Custom Tabs** instead. This prevents Google from blocking sign-in attempts due to security restrictions.

## Troubleshooting  
If you encounter any issues while working on this project, refer to this helpful [Stack Overflow page](https://stackoverflow.com/questions/77587376/cannot-invoke-string-length-because-parameter1-is-null) for solutions.

## License  
This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

## Contributing  
Feel free to fork this repository and submit pull requests. Contributions are welcome!

