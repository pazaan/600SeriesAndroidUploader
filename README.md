[![Build Status](https://travis-ci.org/pazaan/600SeriesAndroidUploader.svg?branch=master)](https://travis-ci.org/pazaan/600SeriesAndroidUploader)
[![Stories in Progress](https://badge.waffle.io/pazaan/600SeriesAndroidUploader.svg?label=in%20progress&title=In%20Progress)](http://waffle.io/pazaan/600SeriesAndroidUploader)

## 600SeriesAndroidUploader

This is an Android app to upload data from a MiniMed 600 Series insulin pump to a Nightscout website via a Contour Next Link 2.4 blood glucose meter

###### [Click here for more info](https://github.com/pazaan/600SeriesAndroidUploader/wiki)
###### [Click here for Releases](https://github.com/pazaan/600SeriesAndroidUploader/releases)
###### [Click here for the Main Project Page](http://pazaan.github.io/600SeriesAndroidUploader/)

<br/>
<a target="blank" href="https://raw.githubusercontent.com/wiki/pazaan/600SeriesAndroidUploader/images/kit-showing-app.jpg"><img src="https://raw.githubusercontent.com/wiki/pazaan/600SeriesAndroidUploader/images/kit-showing-app.jpg" width="200"></a>
<a target="blank" href="https://raw.githubusercontent.com/wiki/pazaan/600SeriesAndroidUploader/images/kit-in-case-1.jpg"><img src="https://raw.githubusercontent.com/wiki/pazaan/600SeriesAndroidUploader/images/kit-in-case-1.jpg" width="200"></a>
<a target="blank" href="https://raw.githubusercontent.com/wiki/pazaan/600SeriesAndroidUploader/images/kit-in-case-2.jpg"><img src="https://raw.githubusercontent.com/wiki/pazaan/600SeriesAndroidUploader/images/kit-in-case-2.jpg" width="200"></a>
<br/><br/>

#### Development - getting started

 - Install [Android Studio](https://developer.android.com/studio/index.html)
 - Install [Fabric Plugin](https://fabric.io), enable Crashlytics, this should create `app/fabric.properties` with your fabric apiSecret and also add a fabric key to `app/src/AndroidManifest.xml`

   ```
   <meta-data
       android:name="io.fabric.ApiKey"
       android:value="YOUR-FABRIC-KEY" />
   ```

   (**Please take care not to commit this change.
      If you're considering sharing your changes, or are using a non-private
      Github repository, you should remove this change in
      `app/src/AndroidManifest.xml` and copy the value as `apiKey` property
      to file `app/fabric.properties` instead. See
      https://docs.fabric.io/android/fabric/settings/working-in-teams.html#android-projects
      for more information.**)
 - Create a [BugFender](https://app.bugfender.com) account, create `app/bugfender.properties` and populate with

   ```
   apiKey=YOUR-BUGFENDER-KEY
   ```
 - Set up a virtual device in the AVD manager or connect an Android phone with USB debugging enabled.

 - Use one of the run configurations, eg `installDebug`
 
#### App Credits
* Based on https://github.com/arbox0/MedtronicUploader *(though the internals are completely changed for the 600 Series pumps)*
* Uses the [android-service-example](https://code.launchpad.net/~binwiederhier/+junk/android-service-example) by Philipp C. Heckel
* Project initiated by [@pazaan](https://github.com/pazaan)

<br/>

#### Disclaimer And Warning

+ All information, thought, and code described here is intended for informational and educational purposes only. Nightscout currently makes no attempt at HIPAA privacy compliance. Use Nightscout at your own risk, and do not use the information or code to make medical decisions.

+ Use of code from github.com is without warranty or formal support of any kind. Please review this repository's [LICENSE](https://github.com/pazaan/600SeriesAndroidUploader/blob/master/LICENSE) for details. 

+ All product and company names, trademarks, servicemarks, registered trademarks, and registered servicemarks are the property of their respective holders. Their use is for information purposes and does not imply any affiliation with or endorsement by them. 

+ Please note - this project has **no** association with and is **not** endorsed by:
 + [Medtronic](http://www.medtronicdiabetes.com/)
 + [Ascensia Diabetes Care (formerly Bayer Diabetes Care)](http://www.ascensia.com/)
