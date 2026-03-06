package com.example.service;

import com.example.model.Auteur;
import com.example.model.Livre;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;

import javax.persistence.*;
import java.util.List;

public class PerformanceTestService {

    private final EntityManagerFactory emf;

    public PerformanceTestService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public void resetStatistics() {
        Session session = emf.createEntityManager().unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        stats.clear();
    }

    public void printStatistics(String testName) {
        Session session = emf.createEntityManager().unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();

        System.out.println("\n=== Statistiques pour " + testName + " ===");
        System.out.println("Requêtes exécutées: " + stats.getQueryExecutionCount());
        System.out.println("Temps d'exécution des requêtes: " + stats.getQueryExecutionMaxTime() + "ms");
        System.out.println("Entités chargées: " + stats.getEntityLoadCount());
        System.out.println("Hits du cache de second niveau: " + stats.getSecondLevelCacheHitCount());
        System.out.println("Miss du cache de second niveau: " + stats.getSecondLevelCacheMissCount());
        System.out.println("Ratio de hit du cache: " +
                (stats.getSecondLevelCacheHitCount() + stats.getSecondLevelCacheMissCount() > 0 ?
                        (double) stats.getSecondLevelCacheHitCount() / (stats.getSecondLevelCacheHitCount() + stats.getSecondLevelCacheMissCount()) : 0));
    }

    // Test 1: Problème N+1 sans optimisation
    public void testN1Problem() {
        resetStatistics();
        long startTime = System.currentTimeMillis();

        EntityManager em = emf.createEntityManager();
        try {
            // Récupération de tous les auteurs
            List<Auteur> auteurs = em.createQuery("SELECT a FROM Auteur a", Auteur.class)
                    .getResultList();

            // Pour chaque auteur, accès à ses livres (génère des requêtes N+1)
            for (Auteur auteur : auteurs) {
                System.out.println("Auteur: " + auteur.getNom() + " " + auteur.getPrenom());
                System.out.println("Nombre de livres: " + auteur.getLivres().size());

                // Accès aux détails des livres
                for (Livre livre : auteur.getLivres()) {
                    System.out.println("  - " + livre.getTitre() + " (" + livre.getAnneePublication() + ")");
                    // Accès aux catégories (génère encore plus de requêtes)
                    System.out.println("    Catégories: " + livre.getCategories().size());
                }
            }

        } finally {
            em.close();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Temps d'exécution: " + (endTime - startTime) + "ms");
        printStatistics("Problème N+1 sans optimisation");
    }

    // Test 2: Résolution du problème N+1 avec JOIN FETCH
    public void testJoinFetch() {
        resetStatistics();
        long startTime = System.currentTimeMillis();

        EntityManager em = emf.createEntityManager();
        try {
            // Récupération des auteurs avec leurs livres en une seule requête
            List<Auteur> auteurs = em.createQuery(
                            "SELECT DISTINCT a FROM Auteur a LEFT JOIN FETCH a.livres", Auteur.class)
                    .getResultList();

            // Pour chaque auteur, accès à ses livres (déjà chargés)
            for (Auteur auteur : auteurs) {
                System.out.println("Auteur: " + auteur.getNom() + " " + auteur.getPrenom());
                System.out.println("Nombre de livres: " + auteur.getLivres().size());

                // Accès aux détails des livres
                for (Livre livre : auteur.getLivres()) {
                    System.out.println("  - " + livre.getTitre() + " (" + livre.getAnneePublication() + ")");
                    // Accès aux catégories (génère encore des requêtes N+1)
                    System.out.println("    Catégories: " + livre.getCategories().size());
                }
            }

        } finally {
            em.close();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Temps d'exécution: " + (endTime - startTime) + "ms");
        printStatistics("Résolution avec JOIN FETCH");
    }

    // Test 3: Résolution du problème N+1 avec Entity Graphs
    public void testEntityGraph() {
        resetStatistics();
        long startTime = System.currentTimeMillis();

        EntityManager em = emf.createEntityManager();
        try {
            // Utilisation d'un entity graph nommé
            EntityGraph<?> graph = em.getEntityGraph("graph.Livre.categoriesEtAuteur");

            // Récupération des livres avec leurs catégories et auteurs en une seule requête
            List<Livre> livres = em.createQuery("SELECT l FROM Livre l", Livre.class)
                    .setHint("javax.persistence.fetchgraph", graph)
                    .getResultList();

            // Accès aux détails des livres
            for (Livre livre : livres) {
                System.out.println("Livre: " + livre.getTitre() + " (" + livre.getAnneePublication() + ")");
                System.out.println("  Auteur: " + livre.getAuteur().getNom() + " " + livre.getAuteur().getPrenom());
                System.out.println("  Catégories: " + livre.getCategories().size());
            }

        } finally {
            em.close();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Temps d'exécution: " + (endTime - startTime) + "ms");
        printStatistics("Résolution avec Entity Graphs");
    }

    // Test 4: Test du cache de second niveau
    public void testSecondLevelCache() {
        System.out.println("\n=== Test du cache de second niveau ===");

        // Premier accès (miss du cache)
        resetStatistics();
        System.out.println("\nPremier accès (miss du cache):");
        EntityManager em1 = emf.createEntityManager();
        try {
            Auteur auteur = em1.find(Auteur.class, 1L);
            System.out.println("Auteur trouvé: " + auteur.getNom() + " " + auteur.getPrenom());
        } finally {
            em1.close();
        }
        printStatistics("Premier accès");

        // Deuxième accès (hit du cache)
        resetStatistics();
        System.out.println("\nDeuxième accès (hit du cache):");
        EntityManager em2 = emf.createEntityManager();
        try {
            Auteur auteur = em2.find(Auteur.class, 1L);
            System.out.println("Auteur trouvé: " + auteur.getNom() + " " + auteur.getPrenom());
        } finally {
            em2.close();
        }
        printStatistics("Deuxième accès");

        // Test avec une requête cachée
        System.out.println("\nTest avec une requête cachée:");

        // Premier accès à la requête
        resetStatistics();
        System.out.println("\nPremier accès à la requête:");
        EntityManager em3 = emf.createEntityManager();
        try {
            TypedQuery<Auteur> query = em3.createQuery(
                            "SELECT a FROM Auteur a WHERE a.nom = :nom", Auteur.class)
                    .setParameter("nom", "Hugo")
                    .setHint("org.hibernate.cacheable", "true");

            List<Auteur> auteurs = query.getResultList();
            for (Auteur auteur : auteurs) {
                System.out.println("Auteur trouvé: " + auteur.getNom() + " " + auteur.getPrenom());
            }
        } finally {
            em3.close();
        }
        printStatistics("Premier accès à la requête");

        // Deuxième accès à la requête
        resetStatistics();
        System.out.println("\nDeuxième accès à la requête:");
        EntityManager em4 = emf.createEntityManager();
        try {
            TypedQuery<Auteur> query = em4.createQuery(
                            "SELECT a FROM Auteur a WHERE a.nom = :nom", Auteur.class)
                    .setParameter("nom", "Hugo")
                    .setHint("org.hibernate.cacheable", "true");

            List<Auteur> auteurs = query.getResultList();
            for (Auteur auteur : auteurs) {
                System.out.println("Auteur trouvé: " + auteur.getNom() + " " + auteur.getPrenom());
            }
        } finally {
            em4.close();
        }
        printStatistics("Deuxième accès à la requête");
    }

    // Test 5: Comparaison des performances avec et sans cache
    public void testPerformanceComparison() {
        System.out.println("\n=== Comparaison des performances avec et sans cache ===");

        // Test sans cache (désactivé temporairement)
        EntityManager em = emf.createEntityManager();
        try {
            // Désactiver le cache pour ce test
            em.unwrap(Session.class).getSessionFactory().getCache().evictAllRegions();

            System.out.println("\nTest sans cache:");
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 100; i++) {
                Auteur auteur = em.find(Auteur.class, (i % 4) + 1L);
                // Forcer le chargement des livres
                auteur.getLivres().size();
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Temps d'exécution sans cache: " + (endTime - startTime) + "ms");

        } finally {
            em.close();
        }

        // Test avec cache
        System.out.println("\nTest avec cache:");
        resetStatistics();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            EntityManager em2 = emf.createEntityManager();
            try {
                Auteur auteur = em2.find(Auteur.class, (i % 4) + 1L);
                // Forcer le chargement des livres
                auteur.getLivres().size();
            } finally {
                em2.close();
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Temps d'exécution avec cache: " + (endTime - startTime) + "ms");
        printStatistics("Test avec cache");
    }
}