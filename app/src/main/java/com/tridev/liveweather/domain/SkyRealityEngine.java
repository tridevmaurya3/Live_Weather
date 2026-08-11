package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import io.github.cosinekitty.astronomy.Aberration;
import io.github.cosinekitty.astronomy.Astronomy;
import io.github.cosinekitty.astronomy.Body;
import io.github.cosinekitty.astronomy.EquatorEpoch;
import io.github.cosinekitty.astronomy.Equatorial;
import io.github.cosinekitty.astronomy.IlluminationInfo;
import io.github.cosinekitty.astronomy.Observer;
import io.github.cosinekitty.astronomy.Refraction;
import io.github.cosinekitty.astronomy.Time;
import io.github.cosinekitty.astronomy.Topocentric;

/**
 * Combines accurate local astronomy with live weather obstruction data.
 *
 * Sun/Moon positions and lunar illumination come from Astronomy Engine.
 * Star visibility and ambient scene light are environmental estimates that
 * react to darkness, clouds, visibility, precipitation and lunar glare.
 */
public final class SkyRealityEngine {

    private SkyRealityEngine() {
    }

    @NonNull
    public static SkyRealityState calculate(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long epochMillis
    ) {
        double elevation = weather.getElevation() == null ? 0.0 : weather.getElevation();
        Observer observer = new Observer(latitude, longitude, elevation);
        Time time = Time.fromMillisecondsSince1970(epochMillis);

        Topocentric sun = horizontalPosition(Body.Sun, time, observer);
        Topocentric moon = horizontalPosition(Body.Moon, time, observer);

        IlluminationInfo moonIllumination = Astronomy.illumination(Body.Moon, time);
        double phaseFraction = clamp(moonIllumination.getPhaseFraction(), 0.0, 1.0);
        double phaseAngle = normalizeDegrees(Astronomy.moonPhase(time));

        WeatherResponse.CurrentWeather current = weather.getCurrent();
        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(weather);

        double cloudPercent = current == null || current.getCloudCover() == null
                ? 0.0
                : clamp(current.getCloudCover(), 0.0, 100.0);
        double visibilityMeters = current == null || current.getVisibility() == null
                ? 12000.0
                : Math.max(0.0, current.getVisibility());
        double precipitation = Math.max(0.0, resolved.getPrecipitationSignalMm());
        boolean precipitationCondition = isPrecipitationCode(resolved.getWeatherCode());

        double darkness = darknessFactor(sun.getAltitude());
        double cloudTransparency = Math.pow(1.0 - (cloudPercent / 100.0), 1.35);
        double visibilityFactor = clamp(visibilityMeters / 20000.0, 0.12, 1.0);
        double precipitationFactor;
        if (precipitation >= 1.0) {
            precipitationFactor = 0.18;
        } else if (precipitation > 0.02) {
            precipitationFactor = 0.48;
        } else if (precipitationCondition) {
            precipitationFactor = 0.38;
        } else {
            precipitationFactor = 1.0;
        }
        double moonGlareFactor = moon.getAltitude() > 0.0
                ? 1.0 - (0.40 * phaseFraction)
                : 1.0;

        int starVisibility = (int) Math.round(100.0
                * darkness
                * cloudTransparency
                * visibilityFactor
                * precipitationFactor
                * moonGlareFactor);
        starVisibility = clampPercent(starVisibility);

        double daylight = daylightFactor(sun.getAltitude());
        double weatherDimmer = 1.0 - (0.30 * (cloudPercent / 100.0));
        if (precipitationCondition) {
            weatherDimmer *= 0.78;
        }
        double moonLight = moon.getAltitude() > 0.0
                ? 0.13 * phaseFraction * cloudTransparency * precipitationFactor
                : 0.0;
        double ambient = Math.max(0.012, daylight * weatherDimmer + moonLight);
        int ambientLight = clampPercent((int) Math.round(ambient * 100.0));

        return new SkyRealityState(
                skyStage(sun.getAltitude()),
                sun.getAltitude(),
                sun.getAzimuth(),
                moon.getAltitude(),
                moon.getAzimuth(),
                phaseFraction * 100.0,
                phaseAngle,
                moonPhaseName(phaseAngle),
                starVisibility,
                ambientLight
        );
    }

    private static Topocentric horizontalPosition(
            Body body,
            Time time,
            Observer observer
    ) {
        Equatorial equatorial = Astronomy.equator(
                body,
                time,
                observer,
                EquatorEpoch.OfDate,
                Aberration.Corrected
        );
        return Astronomy.horizon(
                time,
                observer,
                equatorial.getRa(),
                equatorial.getDec(),
                Refraction.Normal
        );
    }

    private static boolean isPrecipitationCode(Integer code) {
        if (code == null) {
            return false;
        }
        return (code >= 51 && code <= 77)
                || (code >= 80 && code <= 86)
                || code >= 95;
    }

    private static String skyStage(double sunAltitude) {
        if (sunAltitude >= 6.0) {
            return "Daylight";
        }
        if (sunAltitude >= 0.0) {
            return "Golden hour";
        }
        if (sunAltitude >= -6.0) {
            return "Civil twilight";
        }
        if (sunAltitude >= -12.0) {
            return "Nautical twilight";
        }
        if (sunAltitude >= -18.0) {
            return "Astronomical twilight";
        }
        return "Astronomical night";
    }

    private static double darknessFactor(double sunAltitude) {
        if (sunAltitude >= -6.0) {
            return 0.0;
        }
        if (sunAltitude >= -12.0) {
            return interpolate(sunAltitude, -6.0, -12.0, 0.0, 0.42);
        }
        if (sunAltitude >= -18.0) {
            return interpolate(sunAltitude, -12.0, -18.0, 0.42, 1.0);
        }
        return 1.0;
    }

    private static double daylightFactor(double sunAltitude) {
        if (sunAltitude >= 10.0) {
            return 1.0;
        }
        if (sunAltitude >= 0.0) {
            return interpolate(sunAltitude, 0.0, 10.0, 0.72, 1.0);
        }
        if (sunAltitude >= -6.0) {
            return interpolate(sunAltitude, -6.0, 0.0, 0.24, 0.72);
        }
        if (sunAltitude >= -12.0) {
            return interpolate(sunAltitude, -12.0, -6.0, 0.07, 0.24);
        }
        if (sunAltitude >= -18.0) {
            return interpolate(sunAltitude, -18.0, -12.0, 0.018, 0.07);
        }
        return 0.012;
    }

    private static String moonPhaseName(double angle) {
        if (angle < 22.5 || angle >= 337.5) {
            return "New Moon";
        }
        if (angle < 67.5) {
            return "Waxing Crescent";
        }
        if (angle < 112.5) {
            return "First Quarter";
        }
        if (angle < 157.5) {
            return "Waxing Gibbous";
        }
        if (angle < 202.5) {
            return "Full Moon";
        }
        if (angle < 247.5) {
            return "Waning Gibbous";
        }
        if (angle < 292.5) {
            return "Third Quarter";
        }
        return "Waning Crescent";
    }

    private static double interpolate(
            double value,
            double start,
            double end,
            double startValue,
            double endValue
    ) {
        double fraction = (value - start) / (end - start);
        return startValue + (endValue - startValue) * clamp(fraction, 0.0, 1.0);
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
