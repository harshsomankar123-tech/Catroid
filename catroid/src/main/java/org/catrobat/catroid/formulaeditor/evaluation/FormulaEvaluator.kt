/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
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
package org.catrobat.catroid.formulaeditor.evaluation

import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.content.Scope
import org.catrobat.catroid.formulaeditor.FormulaElement
import org.catrobat.catroid.formulaeditor.FormulaElement.ElementType
import org.catrobat.catroid.formulaeditor.Functions
import org.catrobat.catroid.formulaeditor.Operators
import org.catrobat.catroid.formulaeditor.UserDataWrapper
import org.catrobat.catroid.formulaeditor.UserList
import org.catrobat.catroid.formulaeditor.UserVariable
import org.catrobat.catroid.formulaeditor.common.Conversions.FALSE
import org.catrobat.catroid.formulaeditor.common.Conversions.TRUE
import org.catrobat.catroid.formulaeditor.common.Conversions.booleanToDouble
import org.catrobat.catroid.formulaeditor.common.Conversions.convertArgumentToDouble
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretOperatorEqual
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretSensor
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretUserDefinedBrickInput
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretUserList
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.interpretUserVariable
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.isInteger
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.normalizeDegeneratedDoubleValues
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryInterpretCollision
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryInterpretDoubleValue
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryInterpretElementRecursive
import org.catrobat.catroid.formulaeditor.common.FormulaElementOperations.tryParseIntFromObject
import org.catrobat.catroid.formulaeditor.function.ArduinoFunctionProvider
import org.catrobat.catroid.formulaeditor.function.BinaryFunction
import org.catrobat.catroid.formulaeditor.function.FormulaFunction
import org.catrobat.catroid.formulaeditor.function.FunctionProvider
import org.catrobat.catroid.formulaeditor.function.MathFunctionProvider
import org.catrobat.catroid.formulaeditor.function.ObjectDetectorFunctionProvider
import org.catrobat.catroid.formulaeditor.function.RaspiFunctionProvider
import org.catrobat.catroid.formulaeditor.function.TernaryFunction
import org.catrobat.catroid.formulaeditor.function.TextBlockFunctionProvider
import org.catrobat.catroid.formulaeditor.function.TouchFunctionProvider
import org.catrobat.catroid.sensing.ColorAtXYDetection
import org.catrobat.catroid.sensing.ColorCollisionDetection
import org.catrobat.catroid.sensing.ColorEqualsColor
import org.catrobat.catroid.stage.StageActivity
import java.math.BigDecimal
import java.math.MathContext
import java.util.ArrayList
import java.util.EnumMap
import java.util.regex.Pattern

object FormulaEvaluator {
    private val textBlockFunctionProvider = TextBlockFunctionProvider()
    private val formulaFunctions = EnumMap<Functions, FormulaFunction>(Functions::class.java)

    init {
        val functionProviders = listOf(
            ArduinoFunctionProvider(),
            RaspiFunctionProvider(),
            MathFunctionProvider(),
            TouchFunctionProvider(),
            textBlockFunctionProvider,
            ObjectDetectorFunctionProvider()
        )
        for (functionProvider in functionProviders) {
            functionProvider.addFunctionsToMap(formulaFunctions)
        }
        formulaFunctions[Functions.IF_THEN_ELSE] = TernaryFunction { condition, thenValue, elseValue ->
            interpretFunctionIfThenElse(condition, thenValue, elseValue)
        }
    }

    @JvmStatic
    fun interpretRecursive(element: FormulaElement?, scope: Scope?): Any {
        if (element == null) {
            return FALSE
        }
        val rawReturnValue = rawInterpretRecursive(element, scope)
        return normalizeDegeneratedDoubleValues(rawReturnValue)
    }

    private fun rawInterpretRecursive(element: FormulaElement, scope: Scope?): Any {
        val projectManager = ProjectManager.getInstance()
        val currentProject = projectManager?.currentProject
        val currentlyPlayingScene = projectManager?.currentlyPlayingScene
        val currentlyEditedScene = projectManager?.currentlyEditedScene

        return when (element.elementType) {
            ElementType.BRACKET -> {
                if (element.additionalChildren.isNotEmpty()) {
                    interpretRecursive(element.additionalChildren.last(), scope)
                } else {
                    interpretRecursive(element.rightChild, scope)
                }
            }
            ElementType.NUMBER, ElementType.STRING -> element.rawValue
            ElementType.OPERATOR -> tryInterpretOperator(element, scope, element.rawValue)
            ElementType.FUNCTION -> {
                val function = Functions.getFunctionByValue(element.rawValue)
                interpretFunction(element, function, scope)
            }
            ElementType.SENSOR -> {
                if (scope != null && currentlyEditedScene != null && currentProject != null) {
                    interpretSensor(scope.sprite, currentlyEditedScene, currentProject, element.rawValue)
                } else {
                    FALSE
                }
            }
            ElementType.USER_VARIABLE -> {
                val userVariable = UserDataWrapper.getUserVariable(element.rawValue, scope)
                interpretUserVariable(userVariable)
            }
            ElementType.USER_LIST -> {
                val userList = UserDataWrapper.getUserList(element.rawValue, scope)
                interpretUserList(userList)
            }
            ElementType.USER_DEFINED_BRICK_INPUT -> {
                val userBrickVariable = UserDataWrapper.getUserDefinedBrickInput(element.rawValue, scope?.sequence)
                interpretUserDefinedBrickInput(userBrickVariable)
            }
            ElementType.COLLISION_FORMULA -> {
                val stageListener = StageActivity.stageListener
                if (scope != null && currentlyPlayingScene != null) {
                    tryInterpretCollision(scope.sprite.look, element.rawValue, currentlyPlayingScene, stageListener)
                } else {
                    FALSE
                }
            }
            else -> FALSE
        }
    }

    private fun tryInterpretOperator(element: FormulaElement, scope: Scope?, value: String): Any {
        val operator = Operators.getOperatorByValue(value) ?: return false
        return interpretOperator(element, operator, scope)
    }

    private fun interpretOperator(element: FormulaElement, operator: Operators, scope: Scope?): Double {
        return if (element.leftChild != null) {
            interpretBinaryOperator(element, operator, scope)
        } else {
            interpretUnaryOperator(element, operator, scope)
        }
    }

    private fun interpretUnaryOperator(element: FormulaElement, operator: Operators, scope: Scope?): Double {
        val rightObject = tryInterpretElementRecursive(element.rightChild, scope)
        val right = tryInterpretDoubleValue(rightObject)
        return when (operator) {
            Operators.MINUS -> -right
            Operators.LOGICAL_NOT -> booleanToDouble(right == FALSE)
            else -> FALSE
        }
    }

    private fun interpretBinaryOperator(element: FormulaElement, operator: Operators, scope: Scope?): Double {
        val leftObject = tryInterpretElementRecursive(element.leftChild, scope)
        val rightObject = tryInterpretElementRecursive(element.rightChild, scope)

        val leftDouble = tryInterpretDoubleValue(leftObject)
        val rightDouble = tryInterpretDoubleValue(rightObject)

        var left = try {
            BigDecimal.valueOf(tryInterpretDoubleValue(leftObject))
        } catch (e: NumberFormatException) {
            BigDecimal.valueOf(0.0)
        }
        var right = try {
            BigDecimal.valueOf(tryInterpretDoubleValue(rightObject))
        } catch (e: NumberFormatException) {
            BigDecimal.valueOf(0.0)
        }

        val atLeastOneIsNaN = java.lang.Double.isNaN(leftDouble) || java.lang.Double.isNaN(rightDouble)

        return when (operator) {
            Operators.PLUS -> {
                if (atLeastOneIsNaN) {
                    java.lang.Double.NaN
                } else {
                    left.add(right, MathContext.DECIMAL128).toDouble()
                }
            }
            Operators.MINUS -> {
                if (atLeastOneIsNaN) {
                    java.lang.Double.NaN
                } else {
                    left.subtract(right, MathContext.DECIMAL128).toDouble()
                }
            }
            Operators.MULT -> {
                if (atLeastOneIsNaN) {
                    java.lang.Double.NaN
                } else {
                    left.multiply(right, MathContext.DECIMAL128).toDouble()
                }
            }
            Operators.DIVIDE -> {
                if (atLeastOneIsNaN || right == BigDecimal.valueOf(0.0)) {
                    java.lang.Double.NaN
                } else {
                    left.divide(right, MathContext.DECIMAL128).toDouble()
                }
            }
            Operators.POW -> {
                if (atLeastOneIsNaN) {
                    java.lang.Double.NaN
                } else {
                    Math.pow(left.toDouble(), right.toDouble())
                }
            }
            Operators.EQUAL -> booleanToDouble(interpretOperatorEqual(leftObject, rightObject))
            Operators.NOT_EQUAL -> booleanToDouble(!interpretOperatorEqual(leftObject, rightObject))
            Operators.GREATER_THAN -> booleanToDouble(leftDouble.compareTo(rightDouble) > 0)
            Operators.GREATER_OR_EQUAL -> booleanToDouble(leftDouble.compareTo(rightDouble) >= 0)
            Operators.SMALLER_THAN -> booleanToDouble(leftDouble.compareTo(rightDouble) < 0)
            Operators.SMALLER_OR_EQUAL -> booleanToDouble(leftDouble.compareTo(rightDouble) <= 0)
            Operators.LOGICAL_AND -> booleanToDouble(leftDouble != FALSE && rightDouble != FALSE)
            Operators.LOGICAL_OR -> booleanToDouble(leftDouble != FALSE || rightDouble != FALSE)
            else -> FALSE
        }
    }

    private fun interpretFunction(element: FormulaElement, function: Functions, scope: Scope?): Any {
        val arguments = ArrayList<Any?>()
        arguments.add(tryInterpretRecursive(element.leftChild, scope))
        arguments.add(tryInterpretRecursive(element.rightChild, scope))

        for (child in element.additionalChildren) {
            arguments.add(tryInterpretRecursive(child, scope))
        }

        return when (function) {
            Functions.LETTER -> interpretFunctionLetter(arguments[0], arguments[1])
            Functions.RAND -> {
                val arg0 = convertArgumentToDouble(arguments[0])
                val arg1 = convertArgumentToDouble(arguments[1])
                interpretFunctionRand(element, arg0 ?: 0.0, arg1 ?: 0.0)
            }
            Functions.SUBTEXT -> interpretFunctionSubtext(arguments[0], arguments[1], arguments[2])
            Functions.LENGTH -> interpretFunctionLength(element, arguments[0], scope)
            Functions.JOIN -> interpretFunctionJoin(scope, element.leftChild, element.rightChild)
            Functions.JOIN3 -> interpretFunctionJoin3(scope, element.leftChild, element.rightChild, element.additionalChildren)
            Functions.REGEX -> tryInterpretFunctionRegex(scope, element.leftChild, element.rightChild)
            Functions.LIST_ITEM -> interpretFunctionListItem(element, arguments[0], scope)
            Functions.CONTAINS -> interpretFunctionContains(element, arguments[1], scope)
            Functions.NUMBER_OF_ITEMS -> interpretFunctionNumberOfItems(element, arguments[0], scope)
            Functions.INDEX_OF_ITEM -> interpretFunctionIndexOfItem(element, arguments[0], scope)
            Functions.FLATTEN -> interpretFunctionFlatten(scope, element.leftChild)
            Functions.COLLIDES_WITH_COLOR -> {
                if (scope != null) {
                    booleanToDouble(ColorCollisionDetection(scope, StageActivity.stageListener).tryInterpretFunctionTouchesColor(arguments[0]))
                } else {
                    FALSE
                }
            }
            Functions.COLOR_TOUCHES_COLOR -> {
                if (scope != null) {
                    booleanToDouble(ColorCollisionDetection(scope, StageActivity.stageListener).tryInterpretFunctionColorTouchesColor(arguments[0], arguments[1]))
                } else {
                    FALSE
                }
            }
            Functions.COLOR_AT_XY -> {
                if (scope != null) {
                    ColorAtXYDetection(scope, StageActivity.stageListener).tryInterpretFunctionColorAtXY(arguments[0], arguments[1])
                } else {
                    FALSE
                }
            }
            Functions.TEXT_BLOCK_FROM_CAMERA -> textBlockFunctionProvider.interpretFunctionTextBlock(java.lang.Double.parseDouble(arguments[0].toString()))
            Functions.TEXT_BLOCK_LANGUAGE_FROM_CAMERA -> textBlockFunctionProvider.interpretFunctionTextBlockLanguage(java.lang.Double.parseDouble(arguments[0].toString()))
            Functions.COLOR_EQUALS_COLOR -> booleanToDouble(ColorEqualsColor().tryInterpretFunctionColorEqualsColor(arguments[0], arguments[1], arguments[2]))
            else -> interpretFormulaFunction(function, arguments)
        }
    }

    private fun tryInterpretRecursive(element: FormulaElement?, scope: Scope?): Any? {
        return element?.let { interpretRecursive(it, scope) }
    }

    private fun interpretFormulaFunction(function: Functions, arguments: List<Any?>): Any {
        val argumentsDouble = ArrayList<Double?>()
        for (argument in arguments) {
            argumentsDouble.add(convertArgumentToDouble(argument))
        }
        val formulaFunction = formulaFunctions[function] ?: return FALSE
        if (argumentsDouble.size == 2) {
            return formulaFunction.execute(argumentsDouble[0], argumentsDouble[1])
        }
        if (argumentsDouble.size == 3 && argumentsDouble[0] != null && function == Functions.IF_THEN_ELSE) {
            val ifCondition = argumentsDouble[0]!!
            val thenPart = if (arguments[1] is String) arguments[1] else argumentsDouble[1]
            val elsePart = if (arguments[2] is String) arguments[2] else argumentsDouble[2]
            return interpretFunctionIfThenElseObject(ifCondition, thenPart, elsePart)
        }
        return formulaFunction.execute(argumentsDouble[0], argumentsDouble[1], argumentsDouble[2])
    }

    private fun interpretFunctionNumberOfItems(element: FormulaElement, left: Any?, scope: Scope?): Any {
        if (element.leftChild?.elementType == ElementType.USER_LIST) {
            val userList = UserDataWrapper.getUserList(element.leftChild.rawValue, scope)
            return handleNumberOfItemsOfUserListParameter(userList).toDouble()
        }
        return interpretFunctionLength(element, left, scope)
    }

    private fun handleNumberOfItemsOfUserListParameter(userList: UserList?): Int {
        return userList?.value?.size ?: 0
    }

    private fun interpretFunctionContains(element: FormulaElement, right: Any?, scope: Scope?): Any {
        val userList = getUserListOfChild(element.leftChild, scope) ?: return FALSE
        for (userListElement in userList.value) {
            if (interpretOperatorEqual(userListElement, right ?: "")) {
                return TRUE
            }
        }
        return FALSE
    }

    private fun interpretFunctionIndexOfItem(element: FormulaElement, left: Any?, scope: Scope?): Any {
        if (element.rightChild?.elementType == ElementType.USER_LIST) {
            val userList = UserDataWrapper.getUserList(element.rightChild.rawValue, scope) ?: return FALSE
            return (userList.getIndexOf(left) + 1).toDouble()
        }
        return FALSE
    }

    private fun interpretFunctionListItem(element: FormulaElement, left: Any?, scope: Scope?): Any {
        if (left == null) {
            return ""
        }
        val userList = getUserListOfChild(element.rightChild, scope) ?: return ""
        val index = tryParseIntFromObject(left) - 1
        if (index < 0 || index >= userList.value.size) {
            return ""
        }
        return userList.value[index]
    }

    private fun getUserListOfChild(child: FormulaElement?, scope: Scope?): UserList? {
        if (child?.elementType != ElementType.USER_LIST) {
            return null
        }
        return UserDataWrapper.getUserList(child.rawValue, scope)
    }

    private fun interpretFunctionJoin(scope: Scope?, leftChild: FormulaElement?, rightChild: FormulaElement?): String {
        return interpretFunctionString(leftChild, scope) + interpretFunctionString(rightChild, scope)
    }

    private fun interpretFunctionJoin3(
        scope: Scope?,
        leftChild: FormulaElement?,
        rightChild: FormulaElement?,
        additionalChildren: List<FormulaElement>
    ): String {
        return (interpretFunctionString(leftChild, scope) +
            interpretFunctionString(rightChild, scope) +
            interpretFunctionString(additionalChildren.getOrNull(0), scope))
    }

    private fun interpretFunctionFlatten(scope: Scope?, leftChild: FormulaElement?): String {
        return interpretFunctionString(leftChild, scope)
    }

    private fun tryInterpretFunctionRegex(scope: Scope?, leftChild: FormulaElement?, rightChild: FormulaElement?): String {
        return try {
            val left = interpretFunctionString(leftChild, scope)
            val right = interpretFunctionString(rightChild, scope)
            interpretFunctionRegex(left, right)
        } catch (exception: IllegalArgumentException) {
            exception.localizedMessage ?: ""
        }
    }

    private fun interpretFunctionRegex(patternString: String, matcherString: String): String {
        val pattern = Pattern.compile(patternString, Pattern.DOTALL or Pattern.MULTILINE)
        val matcher = pattern.matcher(matcherString)
        return if (matcher.find()) {
            val groupIndex = if (matcher.groupCount() == 0) 0 else 1
            matcher.group(groupIndex) ?: ""
        } else {
            ""
        }
    }

    private fun interpretFunctionString(child: FormulaElement?, scope: Scope?): String {
        var parameterInterpretation = ""
        if (child != null) {
            val objectInterpretation = interpretRecursive(child, scope)
            when (child.elementType) {
                ElementType.STRING -> parameterInterpretation = child.rawValue
                ElementType.NUMBER -> parameterInterpretation = formatNumberString(objectInterpretation.toString())
                else -> {
                    parameterInterpretation += objectInterpretation
                    parameterInterpretation = org.catrobat.catroid.utils.NumberFormats.trimTrailingCharacters(parameterInterpretation)
                }
            }
        }
        return parameterInterpretation
    }

    private fun formatNumberString(numberString: String): String {
        val number = java.lang.Double.parseDouble(numberString)
        var formattedNumberString = ""
        if (!java.lang.Double.isNaN(number)) {
            formattedNumberString += if (isInteger(number)) number.toInt() else number
        }
        return org.catrobat.catroid.utils.NumberFormats.trimTrailingCharacters(formattedNumberString)
    }

    private fun interpretFunctionLength(element: FormulaElement, left: Any?, scope: Scope?): Any {
        val leftChild = element.leftChild ?: return FALSE
        return when (leftChild.elementType) {
            ElementType.NUMBER, ElementType.STRING -> leftChild.rawValue.length.toDouble()
            ElementType.USER_VARIABLE -> {
                val userVariable = UserDataWrapper.getUserVariable(leftChild.rawValue, scope)
                calculateUserVariableLength(userVariable).toDouble()
            }
            ElementType.USER_LIST -> {
                val userList = UserDataWrapper.getUserList(leftChild.rawValue, scope)
                calculateUserListLength(leftChild, userList, left, scope)
            }
            else -> {
                if (left is Double && left.isNaN()) {
                    0.0
                } else {
                    left.toString().length.toDouble()
                }
            }
        }
    }

    private fun calculateUserVariableLength(userVariable: UserVariable?): Int {
        if (userVariable == null) return 0
        val userVariableValue = userVariable.value
        return if (userVariableValue is String) {
            userVariableValue.length
        } else {
            val stringVal = userVariableValue.toString()
            if (stringVal == "true" || stringVal == "false") {
                1
            } else if (userVariableValue is Double && isInteger(userVariableValue)) {
                userVariableValue.toInt().toString().length
            } else {
                stringVal.length
            }
        }
    }

    private fun calculateUserListLength(leftChild: FormulaElement, userList: UserList?, left: Any?, scope: Scope?): Double {
        if (userList == null || userList.value.isEmpty()) {
            return FALSE
        }
        val interpretedList = interpretRecursive(leftChild, scope)
        if (interpretedList is Double) {
            if (interpretedList.isNaN() || interpretedList.isInfinite()) {
                return FALSE
            }
            return interpretedList.toInt().toString().length.toDouble()
        }
        if (interpretedList is String) {
            return interpretedList.length.toDouble()
        }
        if (left is Double && left.isNaN()) {
            return FALSE
        }
        return left.toString().length.toDouble()
    }
    private fun interpretFunctionLetter(left: Any?, right: Any?): Any {
        if (left == null || right == null) {
            return ""
        }
        val index = tryParseIntFromObject(left) - 1
        val stringValueOfRight = right.toString()
        if (index < 0 || index >= stringValueOfRight.length) {
            return ""
        }
        return stringValueOfRight[index].toString()
    }

    private fun interpretFunctionSubtext(leftChild: Any?, rightChild: Any?, string: Any?): Any {
        if (leftChild == null || rightChild == null) {
            return ""
        }
        val start = tryParseIntFromObject(leftChild) - 1
        val end = tryParseIntFromObject(rightChild)
        val stringValueOfString = string.toString()
        if (start < 0 || end < 0 || start > end || end > stringValueOfString.length) {
            return ""
        }
        return stringValueOfString.substring(start, end)
    }

    private fun interpretFunctionRand(from: Double, to: Double): Double {
        val low = Math.min(from, to)
        val high = Math.max(from, to)
        if (low == high) {
            return low
        }
        // In Kotlin, can we check elements?
        // Note: this implementation uses child checking, which isn't easy here, but wait, the original was:
        // isInteger(low) && isInteger(high) && !isNumberWithDecimalPoint(leftChild) && !isNumberWithDecimalPoint(rightChild)
        // Since we don't have leftChild and rightChild references directly here (or wait, we can pass them or check the original element?
        // Wait, yes, we can check the element's children.
        // Let's implement it carefully.
        // Wait, how does interpretFunction call interpretFunctionRand?
        // We can define interpretFunctionRand taking the left and right child nodes or just pass them!
        // Actually, we can define interpretFunctionRand taking: (from: Double, to: Double, leftChild: FormulaElement?, rightChild: FormulaElement?)
        return (Math.random() * (high - low)) + low
    }

    private fun interpretFunctionRand(element: FormulaElement, from: Double, to: Double): Double {
        val low = Math.min(from, to)
        val high = Math.max(from, to)
        if (low == high) {
            return low
        }
        return if (isInteger(low) && isInteger(high) &&
            !isNumberWithDecimalPoint(element.leftChild) && !isNumberWithDecimalPoint(element.rightChild)
        ) {
            Math.floor(Math.random() * ((high + 1) - low)) + low
        } else {
            (Math.random() * (high - low)) + low
        }
    }

    private fun isNumberWithDecimalPoint(element: FormulaElement?): Boolean {
        return element != null && element.elementType == ElementType.NUMBER && element.rawValue.contains(".")
    }

    private fun interpretFunctionIfThenElseObject(condition: Double, thenValue: Any?, elseValue: Any?): Any {
        if (condition.isNaN()) {
            return java.lang.Double.NaN
        }
        return if (condition != 0.0) {
            thenValue ?: FALSE
        } else {
            elseValue ?: FALSE
        }
    }

    private fun interpretFunctionIfThenElse(condition: Double, thenValue: Double, elseValue: Double): Double {
        if (condition.isNaN()) {
            return java.lang.Double.NaN
        }
        return if (condition != 0.0) {
            thenValue
        } else {
            elseValue
        }
    }
}
