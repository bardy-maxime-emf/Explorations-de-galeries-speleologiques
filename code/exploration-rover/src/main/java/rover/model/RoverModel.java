package rover.model;

public class RoverModel {

    private final Connection gestionConnexion;
    private RoverMateriel materielConnecte;

    private boolean estConnecte = false;
    private boolean arretUrgence = false;

    private double vitesseGauche;
    private double vitesseDroite;

    public RoverModel(Connection gestionConnexion) {
        this.gestionConnexion = gestionConnexion;
    }

    // === Connexion (TU n’implémentes pas comment, tu APPELLES Connection) ===

    public void connecter() throws Exception {
        materielConnecte = gestionConnexion.seConnecter();
        estConnecte = true;
        arretUrgence = false;
    }

    public void deconnecter() throws Exception {
        if (materielConnecte != null) {
            materielConnecte.arreter();
            gestionConnexion.seDeconnecter(materielConnecte);
        }
        estConnecte = false;
    }

    // === Commande des roues ===
    public void definirVitesseRoues(double gauche, double droite) throws Exception {
        if (!estConnecte || arretUrgence) return;

        vitesseGauche = gauche;
        vitesseDroite = droite;

        materielConnecte.definirVitesseRoues(gauche, droite);
    }

    public void arretUrgence() throws Exception {
        arretUrgence = true;
        if (materielConnecte != null) {
            materielConnecte.arreter();
        }
    }
}

