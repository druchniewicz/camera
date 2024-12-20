# QED Camera

QED Camera is an Android application designed to work with [ODK Collect](https://github.com/getodk/collect) as an external application for capturing multiple photos (either automatically or manually) and returning them directly to an ODK form.

## Features

- **Automatic Mode** (Default): Captures photos at configurable intervals (e.g., every 5 seconds).
- **Manual Mode**: Allows users to manually take photos.
- **Mode Switching**: Users can switch between automatic and manual modes during an active session.
- **Interval Adjustment**: Users can change the interval between photos during an active session.
- **Automatic Session Termination**: Sessions automatically end when storage limits are reached (e.g., 2.5GB for a group of 10 placeholders).
- **Timed/Photo Count Limits**: Sessions automatically terminate after reaching a specified time limit or photo count.
- **EXIF Data**: Photos include EXIF metadata such as location, timestamp, yaw, pitch, and roll.
- **WebP Compression**: Photos are compressed to WebP format to reduce data size.
- Supports packaging photos into bundles up to 250MB for seamless uploading to a server.
- Configurable parameters for flexible use cases.

## Integration with ODK Collect

QED Camera is initiated from ODK Collect via an intent-based configuration in the ODK form. The form must include a group of questions and use the `intent` column with the following structure:

```
ai.qed.camera(questionNamePrefix='part',maxNumberOfPackages='5',mode='automatic',captureInterval='3')
```

The integration relies on defining a [group of questions](https://docs.getodk.org/collect-external-apps/#external-apps-to-populate-multiple-fields) rather than a [single question](https://docs.getodk.org/collect-external-apps/#designing-an-app-to-return-a-single-value-to-collect) that could be populated by an external app. This approach prevents the creation of a single, excessively large file by dividing the photos into smaller, manageable packages (up to 250MB each), as required by server limitations. It is the responsibility of the form designer to define a group with the appropriate number of questions (placeholders). For example, if a form contains 10 questions, it allows collecting up to 2.5GB of photos (10 x 250MB).

## Sample ODK form

| type         | name   | label | intent                                                                                                   | appearance  |
|--------------|--------|-------|----------------------------------------------------------------------------------------------------------|-------------|
| begin group  | photos |       | ai.qed.camera(questionNamePrefix=’part’, maxNumberOfPackages=’5’, mode=’automatic’, captureInterval=’3’) | field-list  |
| file         | part1  | Part 1|                                                                                                          |             |
| file         | part2  | Part 2|                                                                                                          |             |
| file         | part3  | Part 3|                                                                                                          |             |
| file         | part4  | Part 4|                                                                                                          |             |
| file         | part5  | Part 5|                                                                                                          |             |
| end group    |        |       |                                                                                                          |             |

## Parameters

- **`questionNamePrefix` (Mandatory)**
    - Defines the prefix for question placeholders in the ODK form.
    - Example: For questions named `part1`, `part2`, `part3`, the prefix is `part`.
    - Together with maxNumberOfPackages, this allows the camera application to determine the corresponding question in the ODK form to which the photo bundle should be assigned.
  
- **`maxNumberOfPackages` (Mandatory)**
    - Specifies the number of placeholders defined in the form.
    - Together with maxNumberOfPackages, this allows the camera application to determine the corresponding question in the ODK form to which the photo bundle should be assigned.
  
- **`mode` (Optional)**
    - Specifies the mode of operation: `automatic` or `manual`.
    - Default: `automatic`.

- **`captureInterval` (Optional)**
    - Interval between photos in seconds (only applicable in `automatic` mode).
    - Default: 5 seconds.

- **`maxPhotoCount` (Optional)**
    - Limits the number of photos taken in a single session.
    - Default: No limit.

- **`maxSessionDuration` (Optional)**
    - Sets the maximum session duration in seconds.
    - Default: No limit.

## How to Use

1. **Install ODK Collect and Camera Application**
    - Install ODK Collect and the camera application (QED Camera) on the device.

2. **Create an ODK Form**
    - Define a group of questions with placeholders. In the intent column, specify the package name of the camera application along with the other parameters. This is necessary for launching the external camera application with the correct settings.

3. **Run the ODK Form in Collect**
    - When the relevant section is reached, QED Camera will launch with the configured settings.

4. **Capture Photos**
    - Depending on the mode (`automatic` or `manual`), photos will be captured and packaged.

5. **Return Data to ODK Collect**
    - Packages are sent back to their corresponding placeholders in the form.