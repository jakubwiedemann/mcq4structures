package pl.poznan.put.matching;

import org.apache.commons.collections4.map.DefaultedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.poznan.put.circular.Angle;
import pl.poznan.put.circular.exception.InvalidCircularValueException;
import pl.poznan.put.circular.samples.AngleSample;
import pl.poznan.put.pdb.analysis.PdbCompactFragment;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.torsion.AverageTorsionAngleType;
import pl.poznan.put.torsion.MasterTorsionAngleType;
import pl.poznan.put.torsion.TorsionAngleDelta;
import pl.poznan.put.torsion.TorsionAngleDelta.State;
import pl.poznan.put.torsion.TorsionAngleValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MCQMatcher implements StructureMatcher {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(MCQMatcher.class);

    private List<MasterTorsionAngleType> angleTypes;

    public MCQMatcher(List<MasterTorsionAngleType> angleTypes) {
        super();
        this.angleTypes = angleTypes;
    }

    @Override
    public SelectionMatch matchSelections(StructureSelection target,
                                          StructureSelection model)
            throws InvalidCircularValueException {
        if (target.size() == 0 || model.size() == 0) {
            return new SelectionMatch(target, model,
                                      Collections.<FragmentMatch>emptyList());
        }

        FragmentMatch[][] matrix = fillMatchingMatrix(target, model);
        MCQMatcher.filterMatchingMatrix(matrix);
        List<FragmentMatch> fragmentMatches =
                MCQMatcher.assignFragments(matrix);
        return new SelectionMatch(target, model, fragmentMatches);
    }

    private FragmentMatch[][] fillMatchingMatrix(StructureSelection target,
                                                 StructureSelection model)
            throws InvalidCircularValueException {
        List<PdbCompactFragment> targetFragments = target.getCompactFragments();
        List<PdbCompactFragment> modelFragments = model.getCompactFragments();
        FragmentMatch[][] matrix = new FragmentMatch[targetFragments.size()][];

        for (int i = 0; i < targetFragments.size(); i++) {
            matrix[i] = new FragmentMatch[modelFragments.size()];
        }

        for (int i = 0; i < targetFragments.size(); i++) {
            PdbCompactFragment fi = targetFragments.get(i);
            for (int j = 0; j < modelFragments.size(); j++) {
                PdbCompactFragment fj = modelFragments.get(j);
                if (fi.getMoleculeType() == fj.getMoleculeType()) {
                    matrix[i][j] = matchFragments(fi, fj);
                } else {
                    matrix[i][j] = FragmentMatch.invalidInstance(fi, fj);
                }
            }
        }
        return matrix;
    }

    private static void filterMatchingMatrix(FragmentMatch[][] matrix) {
        Map<PdbCompactFragment, Integer> fragmentMaxCount =
                new DefaultedMap<>(Integer.MIN_VALUE);

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                PdbCompactFragment target = matrix[i][j].getTargetFragment();
                PdbCompactFragment model = matrix[i][j].getModelFragment();
                int count = matrix[i][j].getResidueCount();
                fragmentMaxCount.put(target, Math.max(count, fragmentMaxCount
                        .get(target)));
                fragmentMaxCount.put(model, Math.max(count, fragmentMaxCount
                        .get(model)));
            }
        }

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                PdbCompactFragment target = matrix[i][j].getTargetFragment();
                PdbCompactFragment model = matrix[i][j].getModelFragment();
                int count = matrix[i][j].getResidueCount();
                int maxCount = Math.max(fragmentMaxCount.get(target),
                                        fragmentMaxCount.get(model));

                if (count < maxCount * 0.9) {
                    matrix[i][j] = FragmentMatch.invalidInstance(target, model);
                }
            }
        }
    }

    private static List<FragmentMatch> assignFragments(
            FragmentMatch[][] matrix) {
        return MCQMatcher.assignHungarian(matrix);
    }

    private static List<FragmentMatch> assignHungarian(
            FragmentMatch[][] matrix) {
        double[][] costMatrix = new double[matrix.length][];

        for (int i = 0; i < matrix.length; i++) {
            costMatrix[i] = new double[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                Angle delta = matrix[i][j].getMeanDelta();
                costMatrix[i][j] =
                        delta.isValid() ? delta.getRadians() : Double.MAX_VALUE;
            }
        }

        HungarianAlgorithm algorithm = new HungarianAlgorithm(costMatrix);
        int[] assignment = algorithm.execute();
        List<FragmentMatch> result = new ArrayList<>();

        for (int i = 0; i < assignment.length; i++) {
            int j = assignment[i];
            if (j != -1 && matrix[i][j].isValid()) {
                result.add(matrix[i][j]);
            }
        }

        return result;
    }

    private ResidueComparison compareResidues(PdbCompactFragment targetFragment,
                                              PdbResidue targetResidue,
                                              PdbCompactFragment modelFragment,
                                              PdbResidue modelResidue)
            throws InvalidCircularValueException {
        List<TorsionAngleDelta> angleDeltas = new ArrayList<>();

        for (MasterTorsionAngleType masterType : angleTypes) {
            TorsionAngleDelta delta;

            if (masterType instanceof AverageTorsionAngleType) {
                delta = MCQMatcher.calculateAverageOverTorsionAnglesDifferences(
                        targetFragment, targetResidue, modelFragment,
                        modelResidue, (AverageTorsionAngleType) masterType);
            } else {
                delta = MCQMatcher.findAndSubtractTorsionAngles(targetFragment,
                                                                targetResidue,
                                                                modelFragment,
                                                                modelResidue,
                                                                masterType);
            }

            angleDeltas.add(delta);

            if (MCQMatcher.LOGGER.isTraceEnabled()) {
                MCQMatcher.LOGGER
                        .trace(targetResidue + " vs " + modelResidue + " = "
                               + delta);
            }
        }

        return new ResidueComparison(targetResidue, modelResidue, angleDeltas);
    }

    private static TorsionAngleDelta
    calculateAverageOverTorsionAnglesDifferences(
            PdbCompactFragment targetFragment, PdbResidue targetResidue,
            PdbCompactFragment modelFragment, PdbResidue modelResidue,
            AverageTorsionAngleType averageTorsionAngleType) {
        List<Angle> angles = new ArrayList<>();

        for (MasterTorsionAngleType masterType : averageTorsionAngleType
                .getConsideredAngles()) {
            TorsionAngleDelta delta = MCQMatcher
                    .findAndSubtractTorsionAngles(targetFragment, targetResidue,
                                                  modelFragment, modelResidue,
                                                  masterType);
            if (delta.getState() == State.BOTH_VALID) {
                angles.add(delta.getDelta());
            }
        }

        if (angles.size() == 0) {
            return TorsionAngleDelta
                    .bothInvalidInstance(averageTorsionAngleType);
        }

        AngleSample angleSample = new AngleSample(angles);
        return new TorsionAngleDelta(averageTorsionAngleType, State.BOTH_VALID,
                                     angleSample.getMeanDirection());
    }

    private static TorsionAngleDelta findAndSubtractTorsionAngles(
            PdbCompactFragment targetFragment, PdbResidue targetResidue,
            PdbCompactFragment modelFragment, PdbResidue modelResidue,
            MasterTorsionAngleType masterType) {

        TorsionAngleValue targetValue =
                targetFragment.getTorsionAngleValue(targetResidue, masterType);
        TorsionAngleValue modelValue =
                modelFragment.getTorsionAngleValue(modelResidue, masterType);
        return TorsionAngleDelta
                .subtractTorsionAngleValues(masterType, targetValue,
                                            modelValue);
    }

    @Override
    public FragmentMatch matchFragments(PdbCompactFragment targetFragment,
                                        PdbCompactFragment modelFragment)
            throws InvalidCircularValueException {
        List<PdbResidue> targetResidues = targetFragment.getResidues();
        List<PdbResidue> modelResidues = modelFragment.getResidues();
        boolean isTargetSmaller = targetFragment.size() < modelFragment.size();
        int sizeDifference =
                isTargetSmaller ? modelFragment.size() - targetFragment.size()
                                : targetFragment.size() - modelFragment.size();

        FragmentComparison bestResult = null;
        int bestShift = 0;

        for (int i = 0; i <= sizeDifference; i++) {
            List<ResidueComparison> residueComparisons = new ArrayList<>();

            if (isTargetSmaller) {
                for (int j = 0; j < targetFragment.size(); j++) {
                    PdbResidue targetResidue = targetResidues.get(j);
                    PdbResidue modelResidue = modelResidues.get(j + i);
                    residueComparisons
                            .add(compareResidues(targetFragment, targetResidue,
                                                 modelFragment, modelResidue));
                }
            } else {
                for (int j = 0; j < modelFragment.size(); j++) {
                    PdbResidue targetResidue = targetResidues.get(j + i);
                    PdbResidue modelResidue = modelResidues.get(j);
                    residueComparisons
                            .add(compareResidues(targetFragment, targetResidue,
                                                 modelFragment, modelResidue));
                }
            }

            FragmentComparison fragmentResult = FragmentComparison
                    .fromResidueComparisons(residueComparisons, angleTypes);

            if (bestResult == null
                || fragmentResult.compareTo(bestResult) < 0) {
                bestResult = fragmentResult;
                bestShift = i;
            }
        }

        return new FragmentMatch(targetFragment, modelFragment, isTargetSmaller,
                                 bestShift, bestResult);
    }
}
