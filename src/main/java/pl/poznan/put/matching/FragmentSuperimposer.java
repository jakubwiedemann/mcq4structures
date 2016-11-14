package pl.poznan.put.matching;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Calc;
import org.biojava.nbio.structure.SVDSuperimposer;
import org.biojava.nbio.structure.StructureException;
import pl.poznan.put.atom.AtomName;
import pl.poznan.put.pdb.MmCifPdbIncompatibilityException;
import pl.poznan.put.pdb.PdbAtomLine;
import pl.poznan.put.pdb.PdbResidueIdentifier;
import pl.poznan.put.pdb.analysis.MoleculeType;
import pl.poznan.put.pdb.analysis.PdbCompactFragment;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.pdb.analysis.ResidueComponent;
import pl.poznan.put.protein.ProteinBackbone;
import pl.poznan.put.protein.aminoacid.AminoAcidType;
import pl.poznan.put.rna.Phosphate;
import pl.poznan.put.rna.Ribose;
import pl.poznan.put.rna.base.NucleobaseType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FragmentSuperimposer {
    private final SelectionMatch selectionMatch;
    private final AtomFilter atomFilter;
    private final boolean onlyHeavy;
    private final SVDSuperimposer[] matchSuperimposer;
    private final Atom[][] matchAtomsTarget;
    private final Atom[][] matchAtomsModel;
    private final SVDSuperimposer totalSuperimposer;
    private final Atom[] totalAtomsTarget;
    private final Atom[] totalAtomsModel;
    public FragmentSuperimposer(SelectionMatch selectionMatch,
                                AtomFilter atomFilter, boolean onlyHeavy)
            throws StructureException, MmCifPdbIncompatibilityException {
        super();
        this.selectionMatch = selectionMatch;
        this.atomFilter = atomFilter;
        this.onlyHeavy = onlyHeavy;

        int matchesCount = selectionMatch.getFragmentCount();
        if (matchesCount == 0) {
            throw new IllegalArgumentException(
                    "Failed to superimpose, because the set of structural "
                    + "matches is empty");
        }

        matchSuperimposer = new SVDSuperimposer[matchesCount];
        matchAtomsTarget = new Atom[matchesCount][];
        matchAtomsModel = new Atom[matchesCount][];
        List<Atom> atomsT = new ArrayList<>();
        List<Atom> atomsM = new ArrayList<>();
        filterAtoms(atomsT, atomsM);

        totalAtomsTarget = atomsT.toArray(new Atom[atomsT.size()]);
        totalAtomsModel = atomsM.toArray(new Atom[atomsM.size()]);
        totalSuperimposer =
                new SVDSuperimposer(totalAtomsTarget, totalAtomsModel);
    }

    private void filterAtoms(List<Atom> atomsT, List<Atom> atomsM)
            throws StructureException, MmCifPdbIncompatibilityException {
        int i = 0;

        for (FragmentMatch fragment : selectionMatch.getFragmentMatches()) {
            List<Atom> atomsTarget = new ArrayList<>();
            List<Atom> atomsModel = new ArrayList<>();

            for (ResidueComparison residueComparison : fragment
                    .getResidueComparisons()) {
                PdbResidue target = residueComparison.getTarget();
                PdbResidue model = residueComparison.getModel();
                MoleculeType moleculeType = target.getMoleculeType();
                List<AtomName> atomNames = handleAtomFilter(moleculeType);

                for (AtomName name : atomNames) {
                    if (onlyHeavy && !name.getType().isHeavy()) {
                        continue;
                    }

                    if (!target.hasAtom(name) || !model.hasAtom(name)) {
                        continue;
                    }

                    atomsTarget.add(target.findAtom(name).toBioJavaAtom());
                    atomsModel.add(model.findAtom(name).toBioJavaAtom());
                }
            }

            atomsT.addAll(atomsTarget);
            atomsM.addAll(atomsModel);

            matchAtomsTarget[i] =
                    atomsTarget.toArray(new Atom[atomsTarget.size()]);
            matchAtomsModel[i] =
                    atomsModel.toArray(new Atom[atomsModel.size()]);
            matchSuperimposer[i] = new SVDSuperimposer(matchAtomsTarget[i],
                                                       matchAtomsModel[i]);
            i += 1;
        }
    }

    private List<AtomName> handleAtomFilter(MoleculeType moleculeType) {
        switch (moleculeType) {
            case PROTEIN:
                return handleAtomFilterForProtein();
            case RNA:
                return handleAtomFilterForRNA();
            case UNKNOWN:
            default:
                return Collections.emptyList();
        }
    }

    private List<AtomName> handleAtomFilterForProtein() {
        switch (atomFilter) {
            case ALL:
                Set<AtomName> atomNames = new HashSet<>();
                for (AminoAcidType aminoAcidType : AminoAcidType.values()) {
                    for (ResidueComponent component : aminoAcidType
                            .getAllMoleculeComponents()) {
                        atomNames.addAll(component.getAtoms());
                    }
                }
                return new ArrayList<>(atomNames);
            case BACKBONE:
                return ProteinBackbone.getInstance().getAtoms();
            case MAIN:
                return Collections.singletonList(AtomName.C);
            default:
                return Collections.emptyList();
        }
    }

    private List<AtomName> handleAtomFilterForRNA() {
        Set<AtomName> atomNames = new HashSet<>();

        switch (atomFilter) {
            case ALL:
                for (NucleobaseType nucleobaseType : NucleobaseType.values()) {
                    for (ResidueComponent component : nucleobaseType
                            .getAllMoleculeComponents()) {
                        atomNames.addAll(component.getAtoms());
                    }
                }
                return new ArrayList<>(atomNames);
            case BACKBONE:
                atomNames.addAll(Phosphate.getInstance().getAtoms());
                atomNames.addAll(Ribose.getInstance().getAtoms());
                return new ArrayList<>(atomNames);
            case MAIN:
                return Collections.singletonList(AtomName.P);
            default:
                return Collections.emptyList();
        }
    }

    public int getAtomCount() {
        assert totalAtomsTarget.length == totalAtomsModel.length;
        return totalAtomsTarget.length;
    }

    public double getRMSD() {
        double distance = 0.0;
        double count = 0.0;

        for (int i = 0; i < selectionMatch.getFragmentCount(); i++) {
            for (int j = 0; j < matchAtomsModel[i].length; j++) {
                Atom l = matchAtomsTarget[i][j];
                Atom r = (Atom) matchAtomsModel[i][j].clone();
                Calc.rotate(r, matchSuperimposer[i].getRotation());
                Calc.shift(r, matchSuperimposer[i].getTranslation());

                Vector3D vl = new Vector3D(l.getX(), l.getY(), l.getZ());
                Vector3D vr = new Vector3D(r.getX(), r.getY(), r.getZ());
                distance += vl.distance(vr);
                count += 1.0;
            }
        }

        return Math.sqrt(distance / count);
    }

    public FragmentSuperposition getWhole()
            throws MmCifPdbIncompatibilityException {
        StructureSelection target = selectionMatch.getTarget();
        StructureSelection model = selectionMatch.getModel();
        List<PdbCompactFragment> targetFragments = target.getCompactFragments();
        List<PdbCompactFragment> modelFragments = new ArrayList<>();

        for (PdbCompactFragment fragment : model.getCompactFragments()) {
            List<PdbResidue> modifiedResidues = new ArrayList<>();

            for (PdbResidue residue : fragment.getResidues()) {
                List<PdbAtomLine> modifiedAtoms = new ArrayList<>();

                for (PdbAtomLine atom : residue.getAtoms()) {
                    Atom bioJavaAtom = atom.toBioJavaAtom();
                    Calc.rotate(bioJavaAtom, totalSuperimposer.getRotation());
                    Calc.shift(bioJavaAtom, totalSuperimposer.getTranslation());
                    modifiedAtoms.add(PdbAtomLine.fromBioJavaAtom(bioJavaAtom));
                }

                PdbResidueIdentifier identifier =
                        residue.getResidueIdentifier();
                String residueName = residue.getDetectedResidueName();
                modifiedResidues.add(new PdbResidue(identifier, residueName,
                                                    modifiedAtoms, false));
            }

            modelFragments.add(new PdbCompactFragment(fragment.getName(),
                                                      modifiedResidues));
        }

        return new FragmentSuperposition(targetFragments, modelFragments);
    }

    public FragmentSuperposition getMatched()
            throws MmCifPdbIncompatibilityException {
        List<PdbCompactFragment> newFragmentsL = new ArrayList<>();
        List<PdbCompactFragment> newFragmentsR = new ArrayList<>();

        for (FragmentMatch fragmentMatch : selectionMatch
                .getFragmentMatches()) {
            List<PdbResidue> matchedModelResiduesModified = new ArrayList<>();
            List<PdbResidue> matchedTargetResidues = new ArrayList<>();

            for (ResidueComparison residueComparison : fragmentMatch
                    .getResidueComparisons()) {
                matchedTargetResidues.add(residueComparison.getTarget());

                PdbResidue model = residueComparison.getModel();
                List<PdbAtomLine> modifiedAtoms = new ArrayList<>();

                for (PdbAtomLine atom : model.getAtoms()) {
                    Atom bioJavaAtom = atom.toBioJavaAtom();
                    Calc.rotate(bioJavaAtom, totalSuperimposer.getRotation());
                    Calc.shift(bioJavaAtom, totalSuperimposer.getTranslation());
                    modifiedAtoms.add(PdbAtomLine.fromBioJavaAtom(bioJavaAtom));
                }

                PdbResidueIdentifier identifier = model.getResidueIdentifier();
                String residueName = model.getDetectedResidueName();
                matchedModelResiduesModified
                        .add(new PdbResidue(identifier, residueName,
                                            modifiedAtoms, false));
            }

            newFragmentsL.add(new PdbCompactFragment(
                    fragmentMatch.getModelFragment().getName(),
                    matchedTargetResidues));
            newFragmentsR.add(new PdbCompactFragment(
                    fragmentMatch.getTargetFragment().getName(),
                    matchedModelResiduesModified));
        }

        return new FragmentSuperposition(newFragmentsL, newFragmentsR);
    }

    public enum AtomFilter {
        ALL,
        BACKBONE,
        MAIN
    }
}
