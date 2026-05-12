package com.corba.pdf;

import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.PortableServer.*;
import PDFService.*;

/**
 * Point d'entrée du Serveur CORBA 1.8.
 * Lance l'ORB, enregistre l'objet PDFProcessor dans le NameService,
 * puis attend les appels clients en boucle infinie.
 */
public class PDFServer {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Serveur CORBA PDF 1.8  – Démarrage ║");
        System.out.println("╚══════════════════════════════════════╝");

        try {
            // 1. Initialiser l'ORB (Object Request Broker)
            ORB orb = ORB.init(args, null);
            System.out.println("[ORB] Initialisé");

            // 2. Obtenir la référence vers le POA racine (Portable Object Adapter)
            POA rootPOA = POAHelper.narrow(
                orb.resolve_initial_references("RootPOA")
            );
            rootPOA.the_POAManager().activate();
            System.out.println("[POA] Activé");

            // 3. Créer l'implémentation du service PDF
            PDFProcessorImpl pdfImpl = new PDFProcessorImpl();

            // 4. Enregistrer l'objet auprès du POA et obtenir sa référence CORBA
            org.omg.CORBA.Object ref = rootPOA.servant_to_reference(pdfImpl);
            PDFProcessor pdfRef = PDFProcessorHelper.narrow(ref);
            System.out.println("[IMPL] PDFProcessorImpl instancié");

            // 5. Enregistrer dans le NameService (orbd doit tourner)
            org.omg.CORBA.Object objRef =
                orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            NameComponent path[] = ncRef.to_name("PDFProcessor");
            ncRef.rebind(path, pdfRef);
            System.out.println("[NameService] PDFProcessor enregistré");

            // 6. Afficher l'IOR pour connexion directe (sans NameService)
            System.out.println("[IOR] " + orb.object_to_string(pdfRef));
            System.out.println();
            System.out.println("[CORBA] Serveur prêt — en attente de requêtes...");
            System.out.println("        Appuyer sur Ctrl+C pour arrêter");
            System.out.println();

            // 7. Boucle infinie — traite les requêtes entrantes
            orb.run();

        } catch (UserException e) {
            System.err.println("[ERREUR CORBA] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[ERREUR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
