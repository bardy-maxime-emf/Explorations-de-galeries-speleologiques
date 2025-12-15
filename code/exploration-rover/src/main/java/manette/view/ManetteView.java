package manette.view;

import manette.model.ManetteModel;

public class ManetteView {

    public void renderConsole(ManetteModel m) {
        System.out.printf(
                "Connected=%s | LX=%6.2f LY=%6.2f | RX=%6.2f RY=%6.2f | A=%s B=%s LB=%s RB=%s | Mode=%s | Batt=%s/%s | Vib=%d/%d%n",
                m.isConnected(),
                m.getLeftX(), m.getLeftY(),
                m.getRightX(), m.getRightY(),
                m.isButtonA(), m.isButtonB(),
                m.isButtonLB(), m.isButtonRB(),
                m.getModeVitesse(),
                m.getBatteryLevel(), m.getBatteryType(),
                m.getVibrationLeft(), m.getVibrationRight());
    }
}
