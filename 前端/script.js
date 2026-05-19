const loginForm = document.getElementById("loginForm");
const usernameInput = document.getElementById("用户名");
const passwordInput = document.getElementById("密码");
const togglePassword = document.getElementById("togglePassword");
const errorMessage = document.getElementById("errorMessage");
const purpleChar = document.getElementById("purpleChar");
const blackChar = document.getElementById("blackChar");
const orangeChar = document.getElementById("orangeChar");
const yellowChar = document.getElementById("yellowChar");
const purpleEyes = document.getElementById("purpleEyes");
const blackEyes = document.getElementById("blackEyes");
const orangePupils = document.getElementById("orangePupils");
const yellowPupils = document.getElementById("yellowPupils");

let showPassword = false;
let isTyping = false;
let purplePeekTimeout = null;
let purpleBlinkTimeout = null;
let blackBlinkTimeout = null;
let orangeBlinkTimeout = null;
let yellowBlinkTimeout = null;
let reactionTimeout = null;
let mouseX = 0;
let mouseY = 0;
let passwordValue = "";

function createEyes(container, count) {
  container.innerHTML = "";
  for (let i = 0; i < count; i += 1) {
    const eye = document.createElement("div");
    eye.className = "eye";
    const inner = document.createElement("div");
    inner.className = "eye-inner";
    eye.appendChild(inner);
    container.appendChild(eye);
  }
}

function createPupils(container, count) {
  createEyes(container, count);
}

createEyes(purpleEyes, 2);
createEyes(blackEyes, 2);
createPupils(orangePupils, 2);
createPupils(yellowPupils, 2);

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function setMessage(text, status) {
  if (!errorMessage) return;
  errorMessage.textContent = text;
  errorMessage.classList.remove("success", "error");
  if (status) {
    errorMessage.classList.add(status);
  }
}

function setReaction(status) {
  const chars = [purpleChar, blackChar, orangeChar, yellowChar];
  chars.forEach((char) => {
    if (!char) return;
    char.classList.remove("success", "error");
    if (status) {
      char.classList.add(status);
    }
  });
  clearTimeout(reactionTimeout);
  if (status) {
    reactionTimeout = setTimeout(() => {
      setReaction(null);
      setMessage("", null);
    }, 2200);
  }
}

function moveFace(charElement, offsetX, offsetY, skew) {
  const x = clamp(offsetX / 15, -10, 10);
  const y = clamp(offsetY / 20, -6, 6);
  charElement.style.transform = `translate(${x}px,0) skewX(${skew}deg)`;
}

window.addEventListener("mousemove", (event) => {
  mouseX = event.clientX;
  mouseY = event.clientY;

  const charElements = [purpleChar, blackChar, orangeChar, yellowChar];
  charElements.forEach((element) => {
    if (!element) return;
    const rect = element.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 3;
    const deltaX = mouseX - centerX;
    const deltaY = mouseY - centerY;
    const skew = clamp(-deltaX / 120, -5, 5);
    moveFace(element, deltaX, deltaY, skew);
  });

  updateEyePositions();
});

function updateEyePositions() {
  const allEyes = [
    { container: purpleEyes, max: 4 },
    { container: blackEyes, max: 4 },
    { container: orangePupils, max: 5 },
    { container: yellowPupils, max: 5 },
  ];

  allEyes.forEach(({ container, max }) => {
    const items = Array.from(container.children);
    items.forEach((item) => {
      const pupil = item.querySelector(".eye-inner");
      if (!pupil) return;
      const rect = item.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;
      const deltaX = mouseX - centerX;
      const deltaY = mouseY - centerY;
      const char = container.closest(".char");
      let x;
      let y;
      if (char && char.classList.contains("avoid")) {
        x = -max * 1.4;
        y = 0;
      } else {
        const distance = Math.min(Math.hypot(deltaX, deltaY), max);
        const angle = Math.atan2(deltaY, deltaX);
        x = Math.cos(angle) * distance;
        y = Math.sin(angle) * distance;
      }
      pupil.style.transform = `translate(calc(-50% + ${x}px), calc(-50% + ${y}px))`;
    });
  });
}

function blinkCharacter(charElement) {
  if (!charElement) return;
  charElement.classList.add("blink");
  setTimeout(() => charElement.classList.remove("blink"), 150);
}

function scheduleBlink(charElement) {
  const timeout = Math.random() * 4000 + 2000;
  return setTimeout(() => {
    blinkCharacter(charElement);
    scheduleBlink(charElement);
  }, timeout);
}

purpleBlinkTimeout = scheduleBlink(purpleChar);
blackBlinkTimeout = scheduleBlink(blackChar);
orangeBlinkTimeout = scheduleBlink(orangeChar);
yellowBlinkTimeout = scheduleBlink(yellowChar);

[purpleChar, blackChar, orangeChar, yellowChar].forEach((char) => {
  if (char) {
    char.addEventListener("mouseenter", () => {
      blinkCharacter(char);
    });
  }
});

function startPeekAnimation() {
  clearTimeout(purplePeekTimeout);
  if (passwordValue.length > 0 && showPassword) {
    purplePeekTimeout = setTimeout(() => {
      purpleChar.classList.add("peek");
      setTimeout(() => purpleChar.classList.remove("peek"), 700);
      startPeekAnimation();
    }, Math.random() * 3000 + 2000);
  }
}

passwordInput.addEventListener("input", (event) => {
  passwordValue = event.target.value;
  isTyping = true;
  purpleChar.classList.add("typing");
  blackChar.classList.add("typing");
  setTimeout(() => {
    isTyping = false;
    purpleChar.classList.remove("typing");
    blackChar.classList.remove("typing");
  }, 800);
  startPeekAnimation();
});

// Update avoid logic for both username and password fields
function updateAvoidState() {
  const chars = [purpleChar, blackChar, orangeChar, yellowChar];
  
  if (showPassword) {
    chars.forEach((char) => {
      if (char) char.classList.add("avoid");
    });
  } else {
    chars.forEach((char) => {
      if (char) char.classList.remove("avoid");
    });
  }
  updateEyePositions();
}

usernameInput.addEventListener("focus", () => {
  isTyping = true;
  purpleChar.classList.add("typing");
  blackChar.classList.add("typing");
  updateAvoidState();
});

usernameInput.addEventListener("blur", () => {
  isTyping = false;
  purpleChar.classList.remove("typing", "avoid");
  blackChar.classList.remove("typing", "avoid");
  updateAvoidState();
});

passwordInput.addEventListener("focus", () => {
  isTyping = true;
  purpleChar.classList.add("typing");
  blackChar.classList.add("typing");
  updateAvoidState();
});

passwordInput.addEventListener("blur", () => {
  isTyping = false;
  purpleChar.classList.remove("typing", "avoid");
  blackChar.classList.remove("typing", "avoid");
  updateAvoidState();
});

togglePassword.addEventListener("click", () => {
  showPassword = !showPassword;
  passwordInput.type = showPassword ? "text" : "password";
  togglePassword.textContent = showPassword ? "🙈" : "👁️";
  updateAvoidState();
  startPeekAnimation();
});

// ===== 登录 - 调用后端API =====
loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const username = usernameInput.value.trim();
  const password = passwordInput.value.trim();
  errorMessage.textContent = "";

  if (!username || !password) {
    setMessage("请输入账号和密码", "error");
    setReaction("error");
    return;
  }

  try {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();

    if (data.code === 200) {
      setMessage("登录成功！欢迎, " + (data.data.nickname || data.data.username) + "!", "success");
      setReaction("success");
      localStorage.setItem("token", data.data.token);
      localStorage.setItem("user", JSON.stringify(data.data));
      // 存储Vue内容页兼容的localStorage
      localStorage.setItem("cryd_id", String(data.data.id || 0));
      localStorage.setItem("cryd_token", data.data.token || "");
      localStorage.setItem("cryd_username", data.data.username || "");
      localStorage.setItem("cryd_nickname", data.data.nickname || "");
      localStorage.setItem("cryd_role", data.data.role || "");
      localStorage.setItem("cryd_class", data.data.className || "");
      localStorage.setItem("cryd_studentId", data.data.studentId || "");
      // 根据角色跳转到Vue内容页
      const rolePath = {
        student: "/student/home",
        teacher: "/teacher/home",
        counselor: "/counselor/warnings",
        admin: "/admin/dashboard"
      };
      const targetPath = rolePath[data.data.role] || "/student/home";
      setTimeout(() => { window.location.href = targetPath; }, 800);
    } else {
      setMessage(data.message || "账号或密码错误", "error");
      setReaction("error");
    }
  } catch (e) {
    setMessage("网络错误，请稍后重试", "error");
    setReaction("error");
  }
});

const loginCard = document.getElementById("loginCard");
const registerCard = document.getElementById("registerCard");
const goToRegisterBtn = document.getElementById("goToRegister");
const goToLoginBtn = document.getElementById("goToLogin");
const registerForm = document.getElementById("registerForm");
const registerError = document.getElementById("registerError");
const successModal = document.getElementById("successModal");
const modalOkBtn = document.getElementById("modalOk");
const roleSelect = document.getElementById("role");
const studentIdGroup = document.getElementById("studentIdGroup");
const phoneGroup = document.getElementById("phoneGroup");
const phoneInput = document.getElementById("phone");
const studentIdInput = document.getElementById("studentId");
const registerPasswordInput = document.getElementById("registerPassword");
const confirmPasswordInput = document.getElementById("confirmPassword");
const toggleRegisterPassword = document.getElementById("toggleRegisterPassword");

let showRegisterPassword = false;

// ===== 角色选择变化时显示/隐藏对应字段 =====
roleSelect.addEventListener("change", () => {
  const role = roleSelect.value;
  if (role === "student") {
    studentIdGroup.style.display = "block";
    studentIdInput.required = true;
    phoneGroup.style.display = "none";
    phoneInput.required = false;
    phoneInput.value = "";
  } else if (role === "teacher") {
    studentIdGroup.style.display = "none";
    studentIdInput.required = false;
    studentIdInput.value = "";
    phoneGroup.style.display = "block";
    phoneInput.required = true;
  } else {
    studentIdGroup.style.display = "none";
    studentIdInput.required = false;
    studentIdInput.value = "";
    phoneGroup.style.display = "none";
    phoneInput.required = false;
    phoneInput.value = "";
  }
});

goToRegisterBtn.addEventListener("click", () => {
  loginCard.style.display = "none";
  registerCard.style.display = "block";
});

goToLoginBtn.addEventListener("click", () => {
  registerCard.style.display = "none";
  loginCard.style.display = "block";
  registerError.textContent = "";
});

toggleRegisterPassword.addEventListener("click", () => {
  showRegisterPassword = !showRegisterPassword;
  registerPasswordInput.type = showRegisterPassword ? "text" : "password";
  toggleRegisterPassword.textContent = showRegisterPassword ? "🙈" : "👁️";
});

// ===== 注册 - 调用后端API =====
registerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const role = roleSelect.value;
  const phone = phoneInput.value.trim();
  const studentId = studentIdInput.value.trim();
  const password = registerPasswordInput.value;
  const confirmPassword = confirmPasswordInput.value;

  registerError.textContent = "";

  if (!role) {
    registerError.textContent = "请选择用户身份";
    return;
  }

  // 教师需要手机号
  if (role === "teacher") {
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      registerError.textContent = "请输入正确的手机号";
      return;
    }
  }

  // 学生必须填写学号
  if (role === "student") {
    if (!studentId || studentId.length < 4) {
      registerError.textContent = "学号至少需要4位";
      return;
    }
  }

  if (password.length < 6) {
    registerError.textContent = "密码至少需要6位";
    return;
  }

  if (password !== confirmPassword) {
    registerError.textContent = "两次输入的密码不一致";
    return;
  }

  try {
    const nicknameInput = document.getElementById("nickname");
    const nickname = nicknameInput ? nicknameInput.value.trim() : "";

    if (!nickname) {
      registerError.textContent = "请输入真实姓名";
      return;
    }

    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        phone: role === "teacher" ? phone : "",
        password,
        studentId: role === "student" ? studentId : null,
        role,
        nickname
      })
    });
    const data = await res.json();

    if (data.code === 200) {
      successModal.style.display = "flex";
      setReaction("success");
    } else {
      registerError.textContent = data.message || "注册失败";
    }
  } catch (e) {
    registerError.textContent = "网络错误，请稍后重试";
  }
});

modalOkBtn.addEventListener("click", () => {
  successModal.style.display = "none";
  registerCard.style.display = "none";
  loginCard.style.display = "block";
  registerForm.reset();
  roleSelect.value = "";
  studentIdGroup.style.display = "none";
  studentIdInput.required = false;
  phoneGroup.style.display = "none";
  phoneInput.required = false;
  registerError.textContent = "";
});

window.addEventListener("load", () => {
  updateEyePositions();
});