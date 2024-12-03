package ai.qed.camera

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class DeviceOrientationProvider(
    private val sensorManager: SensorManager?
) : SensorEventListener {
    var azimuth = 0.0
        private set
    var pitch = 0.0
        private set
    var roll = 0.0
        private set

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.values != null && it.values.size >= 3) {
                val rotationMatrix = FloatArray(9)
                val orientationAngles = FloatArray(3)

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                azimuth = Math.toDegrees(orientationAngles[0].toDouble())
                pitch = Math.toDegrees(orientationAngles[1].toDouble())
                roll = Math.toDegrees(orientationAngles[2].toDouble())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun start() {
        sensorManager?.let {
            it.registerListener(
                this,
                it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }
}