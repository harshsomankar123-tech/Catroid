/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2025  The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.formulaeditor;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Scene;
import org.catrobat.catroid.content.Scope;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.formulaeditor.function.ArduinoFunctionProvider;
import org.catrobat.catroid.formulaeditor.function.BinaryFunction;
import org.catrobat.catroid.formulaeditor.function.FormulaFunction;
import org.catrobat.catroid.formulaeditor.function.FunctionProvider;
import org.catrobat.catroid.formulaeditor.function.MathFunctionProvider;
import org.catrobat.catroid.formulaeditor.function.ObjectDetectorFunctionProvider;
import org.catrobat.catroid.formulaeditor.function.RaspiFunctionProvider;
import org.catrobat.catroid.formulaeditor.function.TernaryFunction;
import org.catrobat.catroid.formulaeditor.function.TextBlockFunctionProvider;
import org.catrobat.catroid.formulaeditor.function.TouchFunctionProvider;
import org.catrobat.catroid.sensing.CollisionDetection;
import org.catrobat.catroid.sensing.ColorAtXYDetection;
import org.catrobat.catroid.sensing.ColorCollisionDetection;
import org.catrobat.catroid.sensing.ColorEqualsColor;
import org.catrobat.catroid.stage.StageActivity;
import org.catrobat.catroid.stage.StageListener;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;

import static org.catrobat.catroid.formulaeditor.Functions.IF_THEN_ELSE;
import static org.catrobat.catroid.formulaeditor.InternTokenType.BRACKET_CLOSE;
import static org.catrobat.catroid.formulaeditor.InternTokenType.BRACKET_OPEN;
import static org.catrobat.catroid.formulaeditor.InternTokenType.COLLISION_FORMULA;
import static org.catrobat.catroid.formulaeditor.InternTokenType.FUNCTION_NAME;
import static org.catrobat.catroid.formulaeditor.InternTokenType.FUNCTION_PARAMETERS_BRACKET_CLOSE;
import static org.catrobat.catroid.formulaeditor.InternTokenType.FUNCTION_PARAMETERS_BRACKET_OPEN;
import static org.catrobat.catroid.formulaeditor.InternTokenType.FUNCTION_PARAMETER_DELIMITER;
import static org.catrobat.catroid.formulaeditor.InternTokenType.NUMBER;
import static org.catrobat.catroid.formulaeditor.InternTokenType.OPERATOR;
import static org.catrobat.catroid.formulaeditor.InternTokenType.SENSOR;
import static org.catrobat.catroid.formulaeditor.InternTokenType.STRING;
import static org.catrobat.catroid.formulaeditor.InternTokenType.USER_DEFINED_BRICK_INPUT;
import static org.catrobat.catroid.formulaeditor.InternTokenType.USER_LIST;
import static org.catrobat.catroid.formulaeditor.InternTokenType.USER_VARIABLE;
import static org.catrobat.catroid.formulaeditor.common.Conversions.FALSE;
import static org.catrobat.catroid.formulaeditor.common.Conversions.TRUE;
import static org.catrobat.catroid.formulaeditor.common.Conversions.booleanToDouble;
import static org.catrobat.catroid.formulaeditor.common.Conversions.convertArgumentToDouble;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretOperatorEqual;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretSensor;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretUserDefinedBrickInput;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretUserList;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretUserVariable;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.isInteger;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.normalizeDegeneratedDoubleValues;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryInterpretCollision;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryInterpretDoubleValue;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryInterpretElementRecursive;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryParseIntFromObject;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementResources.addFunctionResources;
import static org.catrobat.catroid.formulaeditor.common.FormulaElementResources.addSensorsResources;
import static org.catrobat.catroid.utils.NumberFormats.trimTrailingCharacters;

public class FormulaElement implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum ElementType {
		OPERATOR, FUNCTION, NUMBER, SENSOR, USER_VARIABLE, USER_LIST, USER_DEFINED_BRICK_INPUT, BRACKET, STRING, COLLISION_FORMULA
	}

	private ElementType type;
	private String value;
	private FormulaElement leftChild = null;
	private FormulaElement rightChild = null;
	public List<FormulaElement> additionalChildren;
	private transient FormulaElement parent;
	protected FormulaElement() {
		additionalChildren = new ArrayList<>();
	}

	public FormulaElement(ElementType type, String value, FormulaElement parent) {
		this();
		this.type = type;
		this.value = value;
		this.parent = parent;
	}

	public FormulaElement(ElementType type, String value, FormulaElement parent, FormulaElement leftChild,
			FormulaElement rightChild) {
		this(type, value, parent);
		this.leftChild = leftChild;
		this.rightChild = rightChild;

		if (leftChild != null) {
			this.leftChild.parent = this;
		}
		if (rightChild != null) {
			this.rightChild.parent = this;
		}
	}

	public FormulaElement(ElementType type, String value, FormulaElement parent, FormulaElement leftChild,
			FormulaElement rightChild, List<FormulaElement> additionalChildren) {
		this(type, value, parent, leftChild, rightChild);
		for (FormulaElement child : additionalChildren) {
			addAdditionalChild(child);
		}
	}



	public ElementType getElementType() {
		return type;
	}

	public String getValue() {
		return trimTrailingCharacters(value);
	}

	public String getRawValue() {
		return value;
	}

	public List<InternToken> getInternTokenList() {
		List<InternToken> tokens = new LinkedList<>();

		switch (type) {
			case BRACKET:
				addBracketTokens(tokens, rightChild);
				break;
			case OPERATOR:
				addOperatorTokens(tokens, value);
				break;
			case FUNCTION:
				addFunctionTokens(tokens, value, leftChild, rightChild);
				break;
			case USER_VARIABLE:
				addToken(tokens, USER_VARIABLE, value);
				break;
			case USER_LIST:
				addToken(tokens, USER_LIST, value);
				break;
			case USER_DEFINED_BRICK_INPUT:
				addToken(tokens, USER_DEFINED_BRICK_INPUT, value);
				break;
			case NUMBER:
				addToken(tokens, NUMBER, trimTrailingCharacters(value));
				break;
			case SENSOR:
				addToken(tokens, SENSOR, value);
				break;
			case STRING:
				addToken(tokens, STRING, value);
				break;
			case COLLISION_FORMULA:
				addToken(tokens, COLLISION_FORMULA, value);
				break;
		}
		return tokens;
	}

	private void addToken(List<InternToken> tokens, InternTokenType tokenType) {
		tokens.add(new InternToken(tokenType));
	}

	private void addToken(List<InternToken> tokens, InternTokenType tokenType, String value) {
		tokens.add(new InternToken(tokenType, value));
	}

	private void addBracketTokens(List<InternToken> internTokenList, FormulaElement element) {
		addToken(internTokenList, BRACKET_OPEN);
		tryAddInternTokens(internTokenList, element);
		addToken(internTokenList, BRACKET_CLOSE);
	}

	private void addOperatorTokens(List<InternToken> tokens, String value) {
		tryAddInternTokens(tokens, leftChild);
		addToken(tokens, OPERATOR, value);
		tryAddInternTokens(tokens, rightChild);
	}

	private void addFunctionTokens(List<InternToken> tokens, String value, FormulaElement leftChild, FormulaElement rightChild) {
		addToken(tokens, FUNCTION_NAME, value);
		boolean functionHasParameters = false;
		if (leftChild != null) {
			addToken(tokens, FUNCTION_PARAMETERS_BRACKET_OPEN);
			functionHasParameters = true;
			tokens.addAll(leftChild.getInternTokenList());
		}
		if (rightChild != null) {
			addToken(tokens, FUNCTION_PARAMETER_DELIMITER);
			tokens.addAll(rightChild.getInternTokenList());
		}
		for (FormulaElement child : additionalChildren) {
			if (child != null) {
				addToken(tokens, FUNCTION_PARAMETER_DELIMITER);
				tokens.addAll(child.getInternTokenList());
			}
		}
		if (functionHasParameters) {
			addToken(tokens, FUNCTION_PARAMETERS_BRACKET_CLOSE);
		}
	}

	private void tryAddInternTokens(List<InternToken> tokens, FormulaElement child) {
		if (child != null) {
			tokens.addAll(child.getInternTokenList());
		}
	}

	public FormulaElement getRoot() {
		FormulaElement root = this;
		while (root.getParent() != null) {
			root = root.getParent();
		}
		return root;
	}

	public void updateElementByName(String oldName, String newName, ElementType type) {
		tryUpdateElementByName(leftChild, oldName, newName, type);
		tryUpdateElementByName(rightChild, oldName, newName, type);

		for (FormulaElement child : additionalChildren) {
			tryUpdateElementByName(child, oldName, newName, type);
		}

		if (matchesTypeAndName(type, oldName)) {
			value = newName;
		}
	}

	private void tryUpdateElementByName(FormulaElement element, String oldName, String newName,
			ElementType type) {
		if (element != null) {
			element.updateElementByName(oldName, newName, type);
		}
	}

	public final boolean containsSpriteInCollision(String name) {
		if (containsSpriteInCollision(leftChild, name) || containsSpriteInCollision(rightChild, name)) {
			return true;
		}
		for (FormulaElement child : additionalChildren) {
			if (containsSpriteInCollision(child, name)) {
				return true;
			}
		}
		return matchesTypeAndName(ElementType.COLLISION_FORMULA, name);
	}

	private boolean containsSpriteInCollision(FormulaElement element, String name) {
		return element != null && element.containsSpriteInCollision(name);
	}

	public final void insertFlattenForAllUserLists(FormulaElement element, FormulaElement parent) {
		if (element.leftChild != null) {
			insertFlattenForAllUserLists(element.leftChild, element);
		}
		if (element.rightChild != null) {
			insertFlattenForAllUserLists(element.rightChild, element);
		}
		for (FormulaElement child : element.additionalChildren) {
			if (child != null) {
				insertFlattenForAllUserLists(child, element);
			}
		}
		if (element.type == ElementType.USER_LIST && isNotUserListFunction(parent)) {
			insertFlattenBetweenParentAndElement(parent, element);
		}
	}

	public boolean isNotUserListFunction(FormulaElement element) {
		return element == null
				|| element.type != ElementType.FUNCTION
				|| (!element.value.equals(Functions.CONTAINS.name())
				&& !element.value.equals(Functions.NUMBER_OF_ITEMS.name())
				&& !element.value.equals(Functions.LIST_ITEM.name())
				&& !element.value.equals(Functions.INDEX_OF_ITEM.name())
				&& !element.value.equals(Functions.FLATTEN.name()));
	}

	public void insertFlattenBetweenParentAndElement(FormulaElement parent,
			FormulaElement element) {
		FormulaElement flatten = new FormulaElement(ElementType.FUNCTION,
				Functions.FLATTEN.name(), parent);
		insertElementBeforeChildInFormulaTree(parent, element, flatten);
	}

	private void insertElementBeforeChildInFormulaTree(FormulaElement parent, FormulaElement child,
			FormulaElement elementToInsert) {
		if (child == null || elementToInsert == null) {
			return;
		}

		child.parent = elementToInsert;
		elementToInsert.setLeftChild(child);

		if (parent == null) {
			return;
		}

		if (parent.leftChild == child) {
			parent.leftChild = elementToInsert;
		} else if (parent.rightChild == child) {
			parent.rightChild = elementToInsert;
		} else {
			for (int i = 0; i < parent.additionalChildren.size(); i++) {
				if (parent.additionalChildren.get(i) == child) {
					parent.additionalChildren.set(i, elementToInsert);
				}
			}
		}
	}

	private boolean matchesTypeAndName(ElementType queriedType, String name) {
		return type == queriedType && value.equals(name);
	}

	public void updateCollisionFormulaToVersion(Project currentProject) {
		tryUpdateCollisionFormulaToVersion(leftChild, currentProject);
		tryUpdateCollisionFormulaToVersion(rightChild, currentProject);
		for (FormulaElement child : additionalChildren) {
			tryUpdateCollisionFormulaToVersion(child, currentProject);
		}
		if (type == ElementType.COLLISION_FORMULA) {
			String secondSpriteName = CollisionDetection.getSecondSpriteNameFromCollisionFormulaString(value, currentProject);
			if (secondSpriteName != null) {
				value = secondSpriteName;
			}
		}
	}

	private void tryUpdateCollisionFormulaToVersion(FormulaElement element, Project currentProject) {
		if (element != null) {
			element.updateCollisionFormulaToVersion(currentProject);
		}
	}

	public FormulaElement getParent() {
		return parent;
	}

	public FormulaElement getLeftChild() {
		return leftChild;
	}

	public FormulaElement getRightChild() {
		return rightChild;
	}

	public void setRightChild(FormulaElement rightChild) {
		this.rightChild = rightChild;
		this.rightChild.parent = this;
	}

	public void setLeftChild(FormulaElement leftChild) {
		this.leftChild = leftChild;
		this.leftChild.parent = this;
	}

	public void addAdditionalChild(FormulaElement child) {
		additionalChildren.add(child);
		child.parent = this;
	}

	public void replaceElement(FormulaElement current) {
		parent = current.parent;
		leftChild = current.leftChild;
		rightChild = current.rightChild;
		for (int index = 0; index < current.additionalChildren.size(); index++) {
			if (index < additionalChildren.size()) {
				additionalChildren.set(index, current.additionalChildren.get(index));
			} else {
				additionalChildren.add(current.additionalChildren.get(index));
			}
		}
		value = current.value;
		type = current.type;

		if (leftChild != null) {
			leftChild.parent = this;
		}
		if (rightChild != null) {
			rightChild.parent = this;
		}
		for (FormulaElement child : additionalChildren) {
			if (child != null) {
				child.parent = this;
			}
		}
	}

	public void replaceElement(ElementType type, String value) {
		this.value = value;
		this.type = type;
	}

	public void replaceWithSubElement(String operator, FormulaElement rightChild) {
		FormulaElement cloneThis = new FormulaElement(ElementType.OPERATOR, operator, this.getParent(), this,
				rightChild);

		cloneThis.parent.rightChild = cloneThis;
	}

	public boolean isBoolean(Scope scope) {
		if (type == ElementType.USER_VARIABLE) {
			return isUserVariableBoolean(scope);
		} else if (type == ElementType.USER_LIST) {
			return isUserListBoolean(scope);
		} else if (type == ElementType.USER_DEFINED_BRICK_INPUT) {
			return isUserDefinedBrickInputBoolean(scope);
		} else {
			return isOtherBooleanFormulaElement();
		}
	}

	private boolean isUserVariableBoolean(Scope scope) {
		UserVariable userVariable = UserDataWrapper.getUserVariable(value, scope);
		return userVariable != null && userVariable.getValue() instanceof Boolean;
	}

	private boolean isUserListBoolean(Scope scope) {
		List<Object> listValues = UserDataWrapper.getUserList(value, scope).getValue();
		if (listValues.size() != 1) {
			return false;
		}
		return listValues.get(0) instanceof Boolean;
	}

	private boolean isUserDefinedBrickInputBoolean(Scope scope) {
		UserData userData = UserDataWrapper.getUserDefinedBrickInput(value, scope.getSequence());
		if (userData != null && userData.getValue() instanceof Formula) {
			return ((Formula) userData.getValue()).getRoot().isBoolean(scope);
		} else {
			return false;
		}
	}

	private boolean isOtherBooleanFormulaElement() {
		return (type == ElementType.FUNCTION
				&& Functions.isBoolean(Functions.getFunctionByValue(value)))
				|| (type == ElementType.SENSOR
				&& Sensors.isBoolean(Sensors.getSensorByValue(value)))
				|| (type == ElementType.OPERATOR
				&& Operators.getOperatorByValue(value).isLogicalOperator)
				|| type == ElementType.COLLISION_FORMULA;
	}

	public boolean containsElement(ElementType elementType) {
		if (type.equals(elementType)
				|| (leftChild != null && leftChild.containsElement(elementType))
				|| (rightChild != null && rightChild.containsElement(elementType))) {
			return true;
		}
		for (FormulaElement child : additionalChildren) {
			if (child != null && child.containsElement(elementType)) {
				return true;
			}
		}
		return false;
	}

	public boolean isNumber() {
		if (type == ElementType.OPERATOR) {
			Operators operator = Operators.getOperatorByValue(value);
			return (operator == Operators.MINUS) && (leftChild == null) && rightChild.isNumber();
		}
		return type == ElementType.NUMBER;
	}

	@Override
	public FormulaElement clone() {
		FormulaElement leftChildClone = tryCloneElement(leftChild);
		FormulaElement rightChildClone = tryCloneElement(rightChild);
		List<FormulaElement> additionalChildrenClones = new ArrayList<>();
		for (FormulaElement child : additionalChildren) {
			additionalChildrenClones.add(tryCloneElement(child));
		}
		String valueClone = value == null ? "" : value;
		return new FormulaElement(type, valueClone, null, leftChildClone, rightChildClone,
				additionalChildrenClones);
	}

	private FormulaElement tryCloneElement(FormulaElement element) {
		return element == null ? null : element.clone();
	}

	public void addRequiredResources(final Set<Integer> requiredResourcesSet) {
		tryAddRequiredResources(requiredResourcesSet, leftChild);
		tryAddRequiredResources(requiredResourcesSet, rightChild);

		for (FormulaElement child : additionalChildren) {
			tryAddRequiredResources(requiredResourcesSet, child);
		}

		switch (type) {
			case FUNCTION:
				addFunctionResources(requiredResourcesSet, Functions.getFunctionByValue(value));
				break;
			case SENSOR:
				addSensorsResources(requiredResourcesSet, Sensors.getSensorByValue(value));
				break;
			case COLLISION_FORMULA:
				requiredResourcesSet.add(Brick.COLLISION);
				break;
			default:
		}
	}

	private void tryAddRequiredResources(Set<Integer> resourceSet, FormulaElement element) {
		if (element != null) {
			element.addRequiredResources(resourceSet);
		}
	}

	public void setValue(String value) {
		this.value = value;
	}

	public List<String> getUserDataRecursive(ElementType type) {
		ArrayList<String> userDataNames = new ArrayList<>();

		if (this.type == type) {
			userDataNames.add(this.value);
		}

		if (this.leftChild != null) {
			userDataNames.addAll(leftChild.getUserDataRecursive(type));
		}

		if (this.rightChild != null) {
			userDataNames.addAll(rightChild.getUserDataRecursive(type));
		}

		for (FormulaElement child : additionalChildren) {
			userDataNames.addAll(child.getUserDataRecursive(type));
		}

		return userDataNames;
	}
}
