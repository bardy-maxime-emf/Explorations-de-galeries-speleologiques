package manette.view;

import manette.model.ManetteModel;

public class ManetteView {

    public void renderConsole(ManetteModel m) {
        System.out.printf(
                "Connected=%s | LX=%6.2f LY=%6.2f | RX=%6.2f RY=%6.2f | B=%s LB=%s RB=%s | Mode=%s | Batt=%s/%s | LinkLost=%s LQ=%.2f | Vib=%d/%d%n",
                m.isConnected(),
                m.getLeftX(), m.getLeftY(),
                m.getRightX(), m.getRightY(),
                m.isButtonB(), m.isButtonLB(), m.isButtonRB(),
                m.getModeVitesse(),
                m.getBatteryLevel(), m.getBatteryType(),
                m.isLinkLost(), m.getLinkQuality(),
                m.getVibrationLeft(), m.getVibrationRight());
    }
}
